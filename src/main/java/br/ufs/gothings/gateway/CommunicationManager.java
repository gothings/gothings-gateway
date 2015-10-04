package br.ufs.gothings.gateway;

import br.ufs.gothings.core.Settings;
import br.ufs.gothings.core.common.Reason;
import br.ufs.gothings.core.message.*;
import br.ufs.gothings.core.common.GatewayException;
import br.ufs.gothings.core.plugin.PluginClient;
import br.ufs.gothings.core.plugin.PluginServer;
import br.ufs.gothings.core.plugin.ReplyLink;
import br.ufs.gothings.core.util.MapUtils;
import br.ufs.gothings.gateway.InterconnectionController.Subscriptions;
import br.ufs.gothings.gateway.block.Block;
import br.ufs.gothings.gateway.block.BlockId;
import br.ufs.gothings.gateway.block.Package;
import br.ufs.gothings.gateway.block.Package.PackageFactory;
import br.ufs.gothings.gateway.block.Package.PackageInfo;
import br.ufs.gothings.gateway.block.Token;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static br.ufs.gothings.gateway.block.BlockId.*;

/**
 * @author Wagner Macedo
 */
public class CommunicationManager {
    private static final Logger logger = LogManager.getFormatterLogger(CommunicationManager.class);

    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService eventExecutor = Executors.newWorkStealingPool();
    private final ThreadGroup pluginsGroup = new ThreadGroup("plugins-ThreadGroup");

    private final AtomicLong sequence = new AtomicLong(0);
    private final Map<String, PluginData> pluginsMap = new ConcurrentHashMap<>();
    private final Map<Long, FutureReply> waitingReplies =
            new ConcurrentHashMap<>();

    private final Map<Block, BlockId> blocksMap = new IdentityHashMap<>();
    private final Token mainToken = new Token();
    private final PackageFactory pkgFactory = Package.getFactory(mainToken);

    private final Subscriptions iccSubscriptions;

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

        // Obtain the Interconnection Controller subscriptions
        iccSubscriptions = ((InterconnectionController) icc).getSubscriptions();
    }

    public void register(final PluginClient client) {
        final String protocol = client.getProtocol();
        final PluginData pd = pluginsMap.computeIfAbsent(protocol, k -> new PluginData(protocol));
        if (pd.client == null) {
            pd.client = client;
        } else {
            throw new IllegalStateException(protocol + " client plugin is already set");
        }

        client.setUp(new ReplyLink() {
            @Override
            public void ack(final Long sequence) {
                final FutureReply future = waitingReplies.remove(sequence);
                if (future != null) {
                    future.complete(GwReply.EMPTY.withSequence(sequence));
                }
            }

            @Override
            public void send(final GwReply reply) {
                final Package pkg = pkgFactory.newPackage();
                final PackageInfo pkgInfo = pkg.getInfo(mainToken);
                pkgInfo.setMessage(reply);
                pkgInfo.setSourceProtocol(protocol);
                forward(COMMUNICATION_MANAGER, INTERCONNECTION_CONTROLLER, pkg);
            }

            @Override
            public void sendError(final GwError error) {
                if (error.getSourceType() == GwMessage.MessageType.REQUEST) {
                    sendFutureException(new GatewayException(error));
                } else {
                    logger.error("client plugin send an error with source other than GwRequest");
                }
            }
        });

        if (logger.isDebugEnabled()) {
            logger.debug("%s client plugin registered with %s", protocol, client.getClass());
        }

    }

    public void register(final PluginServer server) {
        final String protocol = server.getProtocol();
        final PluginData pd = pluginsMap.computeIfAbsent(protocol, k -> new PluginData(protocol));
        if (pd.server == null) {
            pd.server = server;
        } else {
            throw new IllegalStateException(protocol + " server plugin is already set");
        }

        server.setUp(request -> {
            switch (request.headers().getOperation()) {
                case CREATE:
                case READ:
                case UPDATE:
                case DELETE:
                    request.setSequence(sequence.incrementAndGet());
            }

            final Package pkg = pkgFactory.newPackage();
            final PackageInfo pkgInfo = pkg.getInfo(mainToken);
            pkgInfo.setMessage(request);
            pkgInfo.setSourceProtocol(protocol);
            forward(COMMUNICATION_MANAGER, INPUT_CONTROLLER, pkg);

            return pd.addFuture(request);
        });

        if (logger.isDebugEnabled()) {
            logger.debug("%s server plugin registered with %s", protocol, server.getClass());
        }
    }

    public void register(final PluginClient client, final PluginServer server) {
        register(client);
        register(server);
    }

    public void start() {
        for (final PluginData pd : pluginsMap.values()) {
            final Thread pluginThread;
            if (pd.client == pd.server) {
                pluginThread = new Thread(pluginsGroup, pd.client::start);
            } else {
                pluginThread = new Thread(pluginsGroup, () -> {
                    if (pd.client != null) pd.client.start();
                    if (pd.server != null) pd.server.start();
                });
            }
            pluginThread.start();

            if (logger.isInfoEnabled()) {
                logger.info("%s plugin started: client=%-3s server=%s", pd.getProtocol(),
                        pd.client != null
                                ? "yes"
                                : "no",
                        pd.server != null
                                ? "yes(" + pd.server.settings().get(Settings.SERVER_PORT) + ")"
                                : "no");
            }
        }

        timer.scheduleAtFixedRate(this::sweepWaitingReplies, 1, 1, TimeUnit.MINUTES);
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
    }

    public void stop() {
        // don't continue if stop was already called
        synchronized (timer) {
            if (timer.isShutdown()) return;
        }

        timer.shutdown();
        eventExecutor.shutdown();

        pluginsMap.values().forEach(pd -> {
            if (pd.client != null) pd.client.stop();
            if (pd.server != null) pd.server.stop();
            logger.info("%s plugin stopped", pd.getProtocol());
        });

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
                    // if request could not be forwarded to a plugin return an error immediately
                    final GwRequest request = (GwRequest) message;
                    if (!requestToPlugin(request, pkgInfo.getTargetProtocol())) {
                        sendFutureException(new GatewayException(request, Reason.UNAVAILABLE_PLUGIN));
                    }
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

    private boolean requestToPlugin(final GwRequest request, final String targetProtocol) {
        final PluginData pd = pluginsMap.get(targetProtocol);
        if (pd != null && pd.client != null) {
            pd.client.handleRequest(request);
            return true;
        }
        return false;
    }

    private void replyToPlugin(final GwReply reply, final Map<String, Iterable<Long>> replyTo) {
        replyTo.forEach((protocol, sequences) -> {
            final PluginData pd = pluginsMap.get(protocol);
            if (pd.server != null) {
                for (final Long sequence : sequences) {
                    if (sequence == null) {
                        pd.server.handleReply(reply.withSequence(null));
                    } else {
                        pd.provideReply(reply.withSequence(sequence));
                    }
                }
            }
        });
    }

    private class PluginData {
        private final String protocol;

        private PluginClient client;
        private PluginServer server;

        private PluginData(final String protocol) {
            this.protocol = protocol;
        }

        public Future<GwReply> addFuture(final GwRequest request) {
            final FutureReply future = new FutureReply();
            waitingReplies.put(request.getSequence(), future);
            return future;
        }

        public void provideReply(final GwReply reply) {
            try {
                final FutureReply future = waitingReplies.remove(reply.getSequence());
                future.complete(reply);
            } catch (NullPointerException e) {
                logger.error("not found a message with sequence %d to send the reply", reply.getSequence());
            }
        }

        public String getProtocol() {
            return protocol;
        }
    }

    private static class FutureReply extends CompletableFuture<GwReply> {
        private volatile Instant threshold = Instant.now();

        @Override
        public GwReply get() throws InterruptedException, ExecutionException {
            // far future threshold as we don't know how much time is spent waiting here
            threshold = Instant.MAX;
            try {
                return super.get();
            } catch (InterruptedException e) {
                // usually when this exception is catch means program termination,
                // but as we can't be sure, we adjust threshold for now.
                threshold = Instant.now();
                throw e;
            }
        }

        @Override
        public GwReply get(final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            threshold = Instant.now().plusMillis(unit.toMillis(timeout));
            return super.get(timeout, unit);
        }
    }

    void handleError(final Block sourceBlock, final GatewayException gatewayException) {
        if (blocksMap.containsKey(sourceBlock)) {
            sendFutureException(gatewayException);
        } else {
            logger.error("source block instance not found");
        }
    }

    private void sendFutureException(final GatewayException gatewayException) {
        final FutureReply future = waitingReplies.remove(gatewayException.getErrorMessage().getSequence());
        if (future != null) {
            future.completeExceptionally(gatewayException);
        }
    }

    private void sweepWaitingReplies() {
        waitingReplies.entrySet().removeIf(e -> {
            final FutureReply future = e.getValue();

            // If the future hasn't getters this usually means it's been discarded...
            if (future.getNumberOfDependents() < 1) {
                // ...but we double check by verifying if has passed more than 40 seconds since threshold adjust.
                // This is done to don't remove a just created future or a still wanted reply.
                if (Duration.between(future.threshold, Instant.now()).getSeconds() > 40) {
                    iccSubscriptions.remove(e.getKey());
                    future.cancel(true);
                    return true;
                }
            }

            return false;
        });
    }
}
