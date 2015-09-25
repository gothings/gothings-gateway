package br.ufs.gothings.gateway;

import br.ufs.gothings.core.Settings;
import br.ufs.gothings.core.message.GwMessage;
import br.ufs.gothings.core.message.GwReply;
import br.ufs.gothings.core.message.GwRequest;
import br.ufs.gothings.core.plugin.GwPlugin;
import br.ufs.gothings.core.plugin.PluginClient;
import br.ufs.gothings.core.plugin.PluginServer;
import br.ufs.gothings.core.util.MapUtils;
import br.ufs.gothings.gateway.block.Block;
import br.ufs.gothings.gateway.block.BlockId;
import br.ufs.gothings.gateway.block.Package;
import br.ufs.gothings.gateway.block.Package.PackageFactory;
import br.ufs.gothings.gateway.block.Package.PackageInfo;
import br.ufs.gothings.gateway.block.Token;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Timer;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static br.ufs.gothings.gateway.block.BlockId.*;

/**
 * @author Wagner Macedo
 */
public class CommunicationManager {
    private static Logger logger = LogManager.getFormatterLogger(CommunicationManager.class);

    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService eventExecutor = Executors.newWorkStealingPool();
    private final ThreadGroup pluginsGroup = new ThreadGroup("plugins-ThreadGroup");

    private final AtomicLong sequence = new AtomicLong(0);
    private final Map<String, PluginData> pluginsMap = new ConcurrentHashMap<>();
    private final Map<Long, CompletableFuture<GwReply>> waitingReplies = new ConcurrentHashMap<>();

    private final Map<Block, BlockId> blocksMap = new IdentityHashMap<>();
    private final Token mainToken = new Token();
    private final PackageFactory pkgFactory = Package.getFactory(mainToken);

    CommunicationManager() {
        // PackageFactory configuration
        final Token iccToken = new Token();
        pkgFactory.addToken(iccToken,
                Package.MESSAGE,
                Package.TARGET_PROTOCOL,
                Package.REPLY_TO);

        final Block ic = new InputController(this);
        final Block icc = new InterconnectionController(this, iccToken);
        final Block oc = new OutputController(this);

        // Indexing blocks
        blocksMap.put(ic, INPUT_CONTROLLER);
        blocksMap.put(icc, INTERCONNECTION_CONTROLLER);
        blocksMap.put(oc, OUTPUT_CONTROLLER);
    }

    public void register(final GwPlugin plugin) {
        final PluginData pd = new PluginData(plugin);

        if (plugin instanceof PluginServer) {
            ((PluginServer) plugin).setUp(request -> {
                request.setSequence(sequence.incrementAndGet());

                final Package pkg = pkgFactory.newPackage();
                final PackageInfo pkgInfo = pkg.getInfo(mainToken);
                pkgInfo.setMessage(request);
                pkgInfo.setSourceProtocol(plugin.getProtocol());
                forward(COMMUNICATION_MANAGER, INPUT_CONTROLLER, pkg);

                return pd.addFuture(request);
            });
        }

        if (plugin instanceof PluginClient) {
            ((PluginClient) plugin).setUp(reply -> {
                final Package pkg = pkgFactory.newPackage();
                final PackageInfo pkgInfo = pkg.getInfo(mainToken);
                pkgInfo.setMessage(reply);
                pkgInfo.setSourceProtocol(plugin.getProtocol());
                forward(COMMUNICATION_MANAGER, INTERCONNECTION_CONTROLLER, pkg);
            });
        }

        pluginsMap.put(plugin.getProtocol(), pd);
        if (logger.isDebugEnabled()) {
            logger.debug("%s plugin registered with %s", plugin.getProtocol(), plugin.getClass());
        }
    }

    public void start() {
        for (final PluginData pd : pluginsMap.values()) {
            final Thread pluginThread = new Thread(pluginsGroup, pd.plugin::start);
            pluginThread.start();
            if (logger.isInfoEnabled()) {
                logger.info("%s plugin started: client=%-3s server=%s", pd.plugin.getProtocol(),
                        pd.plugin instanceof PluginClient
                                ? "yes"
                                : "no",
                        pd.plugin instanceof PluginServer
                                ? "yes(" + pd.plugin.settings().get(Settings.SERVER_PORT) + ")"
                                : "no");
            }
        }

        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
    }

    public void stop() {
        timer.shutdownNow();
        eventExecutor.shutdownNow();
        pluginsGroup.interrupt();
    }

    public void forward(final Block sourceBlock, final BlockId targetId, final Package pkg) {
        final BlockId sourceId = blocksMap.get(sourceBlock);
        if (sourceId == null) {
            logger.error("source block instance not found");
            return;
        }
        eventExecutor.submit(() -> forward(sourceId, targetId, pkg));
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
                    replyToPlugin((GwReply) message, pkgInfo.getReplyTo());
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

    private void requestToPlugin(final GwRequest request, final String targetProtocol) {
        final PluginData pd = pluginsMap.get(targetProtocol);
        if (pd.plugin instanceof PluginClient) {
            ((PluginClient) pd.plugin).handleRequest(request);
        }
    }

    private void replyToPlugin(final GwReply reply, final Map<String, Iterable<Long>> replyTo) {
        replyTo.forEach((protocol, sequences) -> {
            final PluginData pd = pluginsMap.get(protocol);
            if (pd.plugin instanceof PluginServer) {
                for (final Long sequence : sequences) {
                    if (sequence == null) {
                        ((PluginServer) pd.plugin).handleReply(reply.asReadOnly(null));
                    } else {
                        pd.provideReply(reply.asReadOnly(sequence));
                    }
                }
            }
        });
    }

    private class PluginData {
        private final GwPlugin plugin;

        public PluginData(final GwPlugin plugin) {
            this.plugin = plugin;
        }

        public Future<GwReply> addFuture(final GwRequest request) {
            final CompletableFuture<GwReply> future = new CompletableFuture<>();
            waitingReplies.put(request.getSequence(), future);
            return future;
        }

        public void provideReply(final GwReply reply) {
            final CompletableFuture<GwReply> future = waitingReplies.remove(reply.getSequence());
            if (future != null) {
                future.complete(reply);
            } else {
                logger.error("not found a message with sequence %d to send the reply", reply.getSequence());
            }
        }
    }
}
