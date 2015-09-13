package br.ufs.gothings.gateway;

import br.ufs.gothings.core.GwHeaders;
import br.ufs.gothings.core.GwMessage;
import br.ufs.gothings.core.GwPlugin;
import br.ufs.gothings.core.Settings;
import br.ufs.gothings.core.message.GwReply;
import br.ufs.gothings.core.message.GwRequest;
import br.ufs.gothings.core.message.sink.MessageLink;
import br.ufs.gothings.gateway.block.Block;
import br.ufs.gothings.gateway.block.BlockId;
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
    private final Map<Long, GwPlugin> sequencesMap = new ConcurrentHashMap<>();
    private final Map<Block, BlockId> blocksMap = new IdentityHashMap<>(4);

    private final Block inputController;
    private final Block interconnectionController;
    private final Block outputController;

    CommunicationManager() {
        inputController = new InputController(this);
        interconnectionController = new InterconnectionController(this);
        outputController = new OutputController(this);

        blocksMap.put(null, COMMUNICATION_MANAGER);
        blocksMap.put(inputController, INPUT_CONTROLLER);
        blocksMap.put(interconnectionController, INTERCONNECTION_CONTROLLER);
        blocksMap.put(outputController, OUTPUT_CONTROLLER);
    }

    public void register(final GwPlugin plugin) {
        final MessageLink serverLink = plugin.serverLink();
        if (serverLink != null) {
            serverLink.setUp(msg -> {
                switch (msg.getType()) {
                    // ignore request messages
                    case REQUEST:
                        logger.error("%s server plugin sent a request to the Communication Manager", plugin.getProtocol());
                        break;
                    case REPLY:
                        sequencesMap.put(msg.getSequence(), plugin);
                        inputController.receiveForwarding(COMMUNICATION_MANAGER, msg);
                        break;
                }
            });
        }

        final MessageLink clientLink = plugin.clientLink();
        if (clientLink != null) {
            clientLink.setUp(msg -> {
                switch (msg.getType()) {
                    // ignore reply messages
                    case REPLY:
                        logger.error("%s client plugin sent an reply to the Communication Manager", plugin.getProtocol());
                        break;
                    case REQUEST:
                        interconnectionController.receiveForwarding(COMMUNICATION_MANAGER, msg);
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

    public void forward(final Block sourceBlock, final BlockId targetId, final GwMessage msg) throws InvalidForwardingException {
        final BlockId sourceId = blocksMap.get(sourceBlock);
        switch (sourceId) {
            case INPUT_CONTROLLER:
                switch (targetId) {
                    case INTERCONNECTION_CONTROLLER:
                        interconnectionController.receiveForwarding(sourceId, msg);
                        break;
                    default:
                        throw new InvalidForwardingException("sourceBlock => targetId");
                }
                break;

            case INTERCONNECTION_CONTROLLER:
                switch (targetId) {
                    case COMMUNICATION_MANAGER:
                        requestToPlugin((GwRequest) msg);
                        break;
                    case OUTPUT_CONTROLLER:
                        outputController.receiveForwarding(sourceId, msg);
                        break;
                    default:
                        throw new InvalidForwardingException("sourceBlock => targetId");
                }
                break;

            case OUTPUT_CONTROLLER:
                switch (targetId) {
                    case COMMUNICATION_MANAGER:
                        replyToPlugin((GwReply) msg);
                        break;
                    default:
                        throw new InvalidForwardingException("sourceBlock => targetId");
                }
                break;

            default:
                throw new InvalidForwardingException("sourceBlock");
        }
    }

    private void requestToPlugin(final GwRequest msg) {
        // TODO: method stub
    }

    private void replyToPlugin(final GwReply msg) {
        final Long sequence = msg.getSequence();

        // reply to a sequenced message
        if (sequence != null) {
            final GwPlugin target = sequencesMap.remove(sequence);
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
