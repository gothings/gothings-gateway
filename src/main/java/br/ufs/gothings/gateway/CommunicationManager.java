package br.ufs.gothings.gateway;

import br.ufs.gothings.core.GwMessage;
import br.ufs.gothings.core.GwPlugin;
import br.ufs.gothings.core.Settings;
import br.ufs.gothings.core.message.GwReply;
import br.ufs.gothings.core.message.GwRequest;
import br.ufs.gothings.core.message.sink.MessageLink;
import br.ufs.gothings.core.util.MapUtils;
import br.ufs.gothings.gateway.block.Block;
import br.ufs.gothings.gateway.block.BlockId;
import br.ufs.gothings.gateway.block.Package;
import br.ufs.gothings.gateway.block.Package.PackageInfo;
import br.ufs.gothings.gateway.block.Package.PackageFactory;
import br.ufs.gothings.gateway.block.Token;
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
    private final Map<Block, BlockId> blocksMap = new IdentityHashMap<>();

    private final Token mainToken = new Token();
    private final PackageFactory pkgFactory = Package.getFactory(mainToken);

    CommunicationManager() {
        // PackageFactory configuration
        final Token targetToken = new Token();
        pkgFactory.addToken(targetToken,
                Package.MESSAGE, Package.TARGET_PROTOCOL);

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
                        final Package pkg = pkgFactory.newPackage();
                        final PackageInfo pkgInfo = pkg.getInfo(mainToken);
                        pkgInfo.setMessage(msg);
                        pkgInfo.setSourceProtocol(plugin.getProtocol());
                        forward(COMMUNICATION_MANAGER, INPUT_CONTROLLER, pkg);
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
                        final Package pkg = pkgFactory.newPackage();
                        final PackageInfo pkgInfo = pkg.getInfo(mainToken);
                        pkgInfo.setMessage(msg);
                        pkgInfo.setSourceProtocol(plugin.getProtocol());
                        forward(COMMUNICATION_MANAGER, INTERCONNECTION_CONTROLLER, pkg);
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

    public void forward(final Block sourceBlock, final BlockId targetId, final Package pkg) {
        final BlockId sourceId = blocksMap.get(sourceBlock);
        if (sourceId == null) {
            logger.error("source block instance not found");
            return;
        }
        forward(sourceId, targetId, pkg);
    }

    private void forward(final BlockId sourceId, final BlockId targetId, final Package pkg) {
        // Consider this package invalid if was not created by communication manager
        if (!pkg.isMainToken(mainToken)) {
            logger.error("package main token is not of the communication manager");
            return;
        }

        // Check if source block can forward to target block
        if (!sourceId.canForward(targetId)) {
            logger.error("%s => %s", sourceId, targetId);
            return;
        }

        // Increment this package pass
        final int passes = pkg.incrementPass(mainToken);

        // If target block is the communication manager...
        if (targetId == COMMUNICATION_MANAGER) {
            // ...depending on source the message is handled as a request or a reply
            final PackageInfo pkgInfo = pkg.getInfo(mainToken);
            final GwMessage message = pkgInfo.getMessage();
            switch (sourceId) {
                case INTERCONNECTION_CONTROLLER:
                    // check number of passes
                    if (passes < 3) {
                        logger.error("%d passes at %s => %s", passes, sourceId, targetId);
                        return;
                    }
                    // log message instance errors
                    if (!(message instanceof GwRequest)) {
                        if (logger.isErrorEnabled()) {
                            logger.error("%s plugin sent a %s as request",
                                    pkgInfo.getSourceProtocol(),
                                    message.getClass().getSimpleName());
                        }
                        return;
                    }
                    requestToPlugin((GwRequest) message, pkgInfo.getTargetProtocol());
                    break;
                case OUTPUT_CONTROLLER:
                    // check number of passes
                    if (passes < 3 || passes > 4) {
                        logger.error("%d passes at %s => %s", passes, sourceId, targetId);
                        return;
                    }
                    // log message instance errors
                    if (!(message instanceof GwReply)) {
                        if (logger.isErrorEnabled()) {
                            logger.error("%s plugin sent a %s as reply",
                                    pkgInfo.getSourceProtocol(),
                                    message.getClass().getSimpleName());
                        }
                        return;
                    }
                    replyToPlugin((GwReply) message, pkgInfo.getTargetProtocol());
                    break;
            }
            return;
        }

        // If nothing happens then forwards to the target block
        final Block targetBlock = MapUtils.getKey(blocksMap, targetId);
        assert targetBlock != null;
        try {
            targetBlock.receiveForwarding(sourceId, pkg);
        } catch (Exception e) {
            if (logger.isErrorEnabled()) {
                logger.error(targetId + " failed to handle forwarding from " + sourceId, e);
            }
        }
    }

    private void requestToPlugin(final GwRequest msg, final String targetProtocol) {
        // TODO: method stub
    }

    private void replyToPlugin(final GwReply msg, final String targetProtocol) {
        // TODO: method stub
    }
}
