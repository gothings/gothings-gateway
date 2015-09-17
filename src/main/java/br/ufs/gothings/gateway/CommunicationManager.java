package br.ufs.gothings.gateway;

import br.ufs.gothings.core.GwHeaders;
import br.ufs.gothings.core.GwPlugin;
import br.ufs.gothings.core.Settings;
import br.ufs.gothings.core.message.GwReply;
import br.ufs.gothings.core.message.GwRequest;
import br.ufs.gothings.core.message.sink.MessageLink;
import br.ufs.gothings.core.util.MapUtils;
import br.ufs.gothings.gateway.block.Block;
import br.ufs.gothings.gateway.block.BlockId;
import br.ufs.gothings.gateway.block.Forwarding;
import br.ufs.gothings.gateway.block.Forwarding.InfoName;
import br.ufs.gothings.gateway.block.Token;
import br.ufs.gothings.gateway.exceptions.InvalidForwardingException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static br.ufs.gothings.gateway.block.BlockId.*;

/**
 * @author Wagner Macedo
 */
public class CommunicationManager {
    private static Logger logger = LogManager.getFormatterLogger(CommunicationManager.class);

    private final Map<String, GwPlugin> pluginsMap = new ConcurrentHashMap<>();
    private final Map<Long, GwPlugin> requestsMap = new ConcurrentHashMap<>();
    private final Map<Block, BlockId> blocksMap = new IdentityHashMap<>();

    private final Token mainToken = new Token();
    private final Token targetToken = new Token();

    CommunicationManager() {
        final Block ic = new InputController(this);
        final Block icc = new InterconnectionController(this, targetToken);
        final Block oc = new OutputController(this);

        // Indexing blocks
        blocksMap.put(ic, INPUT_CONTROLLER);
        blocksMap.put(icc, INTERCONNECTION_CONTROLLER);
        blocksMap.put(oc, OUTPUT_CONTROLLER);
    }

    public void register(final GwPlugin plugin) {
        final MessageLink serverLink = plugin.serverLink();
        if (serverLink != null) {
            serverLink.setUp(msg -> {
                switch (msg.getType()) {
                    // ignore reply messages
                    case REQUEST:
                        requestsMap.put(msg.getSequence(), plugin);
                        final Forwarding fwd = new Forwarding(msg, mainToken);
                        fwd.setExtraInfo(mainToken, InfoName.SOURCE_PROTOCOL, plugin.getProtocol());
                        fwd.addExtraInfoToken(mainToken, targetToken, InfoName.TARGET_PROTOCOL);
                        forward(COMMUNICATION_MANAGER, INPUT_CONTROLLER, fwd);
                        break;
                    case REPLY:
                        logger.error("%s server plugin sent a request to the Communication Manager", plugin.getProtocol());
                        break;
                }
            });
        }

        final MessageLink clientLink = plugin.clientLink();
        if (clientLink != null) {
            clientLink.setUp(msg -> {
                switch (msg.getType()) {
                    // ignore request messages
                    case REQUEST:
                        logger.error("%s client plugin sent an reply to the Communication Manager", plugin.getProtocol());
                        break;
                    case REPLY:
                        final Forwarding fwd = new Forwarding(msg, mainToken);
                        fwd.setExtraInfo(mainToken, InfoName.SOURCE_PROTOCOL, plugin.getProtocol());
                        fwd.addExtraInfoToken(mainToken, targetToken, InfoName.TARGET_PROTOCOL);
                        forward(COMMUNICATION_MANAGER, INTERCONNECTION_CONTROLLER, fwd);
                        break;
                }
            });
        }

        pluginsMap.put(plugin.getProtocol(), plugin);
        if (logger.isDebugEnabled()) {
            logger.debug("%s plugin registered with %s", plugin.getProtocol(), plugin.getClass());
        }
    }

    public void start() {
        for (final GwPlugin plugin : pluginsMap.values()) {
            plugin.start();
            if (logger.isInfoEnabled()) {
                logger.info("%s plugin started: client=%-3s server=%s", plugin.getProtocol(),
                        plugin.clientLink() == null ? "no" : "yes",
                        plugin.serverLink() == null ? "no" : "yes(" + plugin.settings().get(Settings.SERVER_PORT) + ")");
            }
        }
    }

    public void forward(final Block sourceBlock, final BlockId targetId, final Forwarding fwd) throws InvalidForwardingException {
        final BlockId sourceId = blocksMap.get(sourceBlock);
        if (sourceId == null) {
            throw new InvalidForwardingException("source block instance not found");
        }
        forward(sourceId, targetId, fwd);
    }

    private void forward(final BlockId sourceId, final BlockId targetId, final Forwarding fwd) throws InvalidForwardingException {
        // Consider this forwarding invalid if was not created by communication manager
        if (!fwd.isMainToken(mainToken)) {
            throw new InvalidForwardingException("forwarding token is not of communication manager");
        }

        // Check if source block can forward to target block
        if (!sourceId.canForward(targetId)) {
            throw new InvalidForwardingException(sourceId + " => " + targetId);
        }

        // Increment this forwarding pass
        fwd.pass(mainToken);

        // If target block is the communication manager...
        if (targetId == COMMUNICATION_MANAGER) {
            // ...depending on source the message is handled as a request or a reply
            switch (sourceId) {
                case INTERCONNECTION_CONTROLLER:
                    // check number of passes
                    if (fwd.getPasses() < 3) {
                        throw new InvalidForwardingException("invalid number of passes");
                    }
                    requestToPlugin((GwRequest) fwd.getMessage(), fwd.getExtraInfo(InfoName.TARGET_PROTOCOL));
                    break;
                case OUTPUT_CONTROLLER:
                    // check number of passes
                    final int passes = fwd.getPasses();
                    if (passes < 3 || passes > 4) {
                        throw new InvalidForwardingException("invalid number of passes");
                    }
                    replyToPlugin((GwReply) fwd.getMessage(), fwd.getExtraInfo(InfoName.TARGET_PROTOCOL));
                    break;
            }
            return;
        }

        // If nothing happens then forwards to the target block
        final Block targetBlock = MapUtils.getKey(blocksMap, targetId);
        assert targetBlock != null;
        targetBlock.receiveForwarding(sourceId, fwd);
    }

    private void requestToPlugin(final GwRequest msg, final String targetProtocol) {
        // TODO: method stub
    }

    private void replyToPlugin(final GwReply msg, final String targetProtocol) {
        final Long sequence = msg.getSequence();

        // reply to a sequenced message
        if (sequence != null) {
            final GwPlugin target = requestsMap.remove(sequence);
            if (target == null) {
                return;
            }
            // TODO: stub
        }

        // reply to an unsequenced message
        else {
            final GwHeaders h = msg.headers();
            // TODO: stub
        }
    }
}
