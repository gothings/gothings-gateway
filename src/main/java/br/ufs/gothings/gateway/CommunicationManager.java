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

    CommunicationManager() {
        final Block ic = new InputController(this);
        final Block icc = new InterconnectionController(this);
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
                        final Forwarding fwd = new Forwarding(msg, null);
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
                        final Forwarding fwd = new Forwarding(msg, null);
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
        // Check if source block can forward to target block
        if (!sourceId.canForward(targetId)) {
            throw new InvalidForwardingException(sourceId + " => " + targetId);
        }

        // Only forwards if was not forwarded before
        if (!fwd.markUsed()) {
            throw new InvalidForwardingException("wrapper already forwarded");
        }

        // If target block is the communication manager...
        if (targetId == COMMUNICATION_MANAGER) {
            // ...depending on source the message is handled as a request or a reply
            switch (sourceId) {
                case INTERCONNECTION_CONTROLLER:
                    requestToPlugin((GwRequest) fwd.getMessage());
                    break;
                case OUTPUT_CONTROLLER:
                    replyToPlugin((GwReply) fwd.getMessage());
                    break;
            }
            return;
        }

        // If nothing happens then forwards to the target block
        final Block targetBlock = MapUtils.getKey(blocksMap, targetId);
        assert targetBlock != null;
        targetBlock.receiveForwarding(sourceId, fwd);
    }

    private void requestToPlugin(final GwRequest msg) {
        // TODO: method stub
    }

    private void replyToPlugin(final GwReply msg) {
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
