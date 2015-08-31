package br.ufs.gothings.gateway;

import br.ufs.gothings.core.GwMessage;
import br.ufs.gothings.core.GwPlugin;
import br.ufs.gothings.gateway.block.Block;
import br.ufs.gothings.gateway.block.BlockId;
import br.ufs.gothings.core.sink.SinkLink;
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
        final SinkLink<GwMessage> serverLink = plugin.serverLink();
        if (serverLink != null) {
            serverLink.setListener(msg -> {
                // ignore answer messages
                if (!msg.isAnswer()) {
                    inputController.receiveForwarding(COMMUNICATION_MANAGER, msg);
                }
            });
        }

        final SinkLink<GwMessage> clientLink = plugin.clientLink();
        if (clientLink != null) {
            clientLink.setListener(msg -> {
                // ignore request messages
                if (msg.isAnswer()) {
                    interconnectionController.receiveForwarding(COMMUNICATION_MANAGER, msg);
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
                        plugin.serverLink() == null ? "no" : "yes(" + plugin.settings().get("server.port") + ")");
            }
        }
    }

    public void forward(final Block sourceBlock, final BlockId targetId, final GwMessage msg) {
        final BlockId sourceId = blocksMap.get(sourceBlock);
        switch (sourceId) {
            case INPUT_CONTROLLER:
                switch (targetId) {
                    case INTERCONNECTION_CONTROLLER:
                        interconnectionController.receiveForwarding(sourceId, msg);
                        break;
                    default:
                        throw new IllegalArgumentException("sourceBlock => targetId");
                }
                break;

            case INTERCONNECTION_CONTROLLER:
                switch (targetId) {
                    case COMMUNICATION_MANAGER:
                        requestToPlugin(msg);
                        break;
                    case OUTPUT_CONTROLLER:
                        outputController.receiveForwarding(sourceId, msg);
                        break;
                    default:
                        throw new IllegalArgumentException("sourceBlock => targetId");
                }
                break;

            case OUTPUT_CONTROLLER:
                switch (targetId) {
                    case COMMUNICATION_MANAGER:
                        responseToPlugin(msg);
                        break;
                    default:
                        throw new IllegalArgumentException("sourceBlock => targetId");
                }
                break;

            default:
                throw new IllegalArgumentException("sourceBlock");
        }
    }

    private void requestToPlugin(final GwMessage msg) {

    }

    private void responseToPlugin(final GwMessage msg) {
    }
}
