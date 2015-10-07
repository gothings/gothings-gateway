package br.ufs.gothings.gateway;

import br.ufs.gothings.core.Settings;
import br.ufs.gothings.core.common.Reason;
import br.ufs.gothings.core.message.*;
import br.ufs.gothings.core.common.GatewayException;
import br.ufs.gothings.core.plugin.PluginClient;
import br.ufs.gothings.core.plugin.PluginServer;
import br.ufs.gothings.core.plugin.ReplyLink;
import br.ufs.gothings.gateway.InterconnectionController.ObserveList;
import br.ufs.gothings.gateway.block.*;
import br.ufs.gothings.gateway.block.Package;
import br.ufs.gothings.gateway.block.Package.PackageFactory;
import br.ufs.gothings.gateway.block.Package.PackageInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Wagner Macedo
 */
public class CommunicationManager {
    private static final Logger logger = LogManager.getFormatterLogger(CommunicationManager.class);

    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService eventExecutor = Executors.newWorkStealingPool();
    private final ThreadGroup pluginsGroup = new ThreadGroup("plugins-ThreadGroup");

    private final AtomicLong sequenceGen = new AtomicLong(0);
    private final Map<String, PluginData> pluginsMap = new ConcurrentHashMap<>();
    private final Map<Long, FutureReply> waitingReplies =
            new ConcurrentHashMap<>();

    private final Token mainToken = new Token();
    private final PackageFactory pkgFactory = Package.getFactory(mainToken);

    private final Block inputC;
    private final Block interConnC;
    private final Block outputC;
    private final ObserveList iccObserving;

    CommunicationManager() {
        // PackageFactory configuration
        final Token iccToken = new Token();
        pkgFactory.addToken(iccToken,
                Package.MESSAGE,
                Package.TARGET_PROTOCOL,
                Package.REPLY_TO);

        inputC = new InputController();
        interConnC = new InterconnectionController(iccToken);
        outputC = new OutputController();

        // Obtain the Interconnection Controller observing list
        iccObserving = ((InterconnectionController) interConnC).getObserveList();
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
            public void ack(final long sequence) {
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
                processReply(pkg);
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
                    request.setSequence(sequenceGen.updateAndGet(i -> ++i == 0 ? 1 : i));
                    break;
                case OBSERVE:
                case UNOBSERVE:
                    request.setSequence(0);
                    break;
            }

            final Package pkg = pkgFactory.newPackage();
            final PackageInfo pkgInfo = pkg.getInfo(mainToken);
            pkgInfo.setMessage(request);
            pkgInfo.setSourceProtocol(protocol);
//            forward(COMMUNICATION_MANAGER, INPUT_CONTROLLER, pkg);
            processRequest(pkg);

            // Requests with OBSERVE sequence don't wait for a reply
            if (request.getSequence() != 0) {
                return pd.addFuture(request);
            }
            return null;
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

    private void processRequest(final Package pkg) {
        eventExecutor.submit(() -> {
            // Input controller processing
            try {
                inputC.process(pkg);
            } catch (Exception e) {
                errorToPlugin(pkg.getInfo(), e);
            }
            // Interconnection controller processing
            final GwMessage message = pkg.getInfo().getMessage();
            try {
                interConnC.process(pkg);
            } catch (Exception e) {
                errorToPlugin(pkg.getInfo(), e);
            }
            // If ICC left a request, then it's a work for a plugin
            if (message instanceof GwRequest) {
                final GwRequest request = (GwRequest) message;
                if (!requestToPlugin(request.readOnly(), pkg.getInfo().getTargetProtocol())) {
                    sendFutureException(new GatewayException(request, Reason.UNAVAILABLE_PLUGIN));
                }
            }
            // On the other hand, if ICC left a reply, then pass to OC to continue processing
            else if (message instanceof GwReply) {
                final GwReply reply = (GwReply) message;
                try {
                    outputC.process(pkg);
                    replyToPlugin(reply.readOnly(), pkg.getInfo().getReplyTo());
                } catch (Exception e) {
                    errorToPlugin(pkg.getInfo(), e);
                }
            }
        });
    }

    private void processReply(final Package pkg) {
        eventExecutor.submit(() -> {
            final GwReply reply = (GwReply) pkg.getInfo().getMessage();

            // Interconnection controller processing
            try {
                interConnC.process(pkg);
            } catch (Exception e) {
                if (e instanceof StopProcessException) {
                    throw (StopProcessException) e;
                }
                throw new StopProcessException();
            }
            // Output controller processing
            try {
                outputC.process(pkg);
            } catch (Exception e) {
                if (e instanceof StopProcessException) {
                    throw (StopProcessException) e;
                }
                throw new StopProcessException();
            }
            replyToPlugin(reply.readOnly(), pkg.getInfo().getReplyTo());
        });
    }

    private boolean requestToPlugin(final GwRequest request, final String targetProtocol) {
        final PluginData pd = pluginsMap.get(targetProtocol);
        if (pd != null && pd.client != null) {
            pd.client.handleRequest(request);
            return true;
        }
        return false;
    }

    private void replyToPlugin(final GwReply reply, final Map<String, long[]> replyTo) {
        replyTo.forEach((protocol, sequences) -> {
            final PluginData pd = pluginsMap.get(protocol);
            if (pd.server != null) {
                for (final long sequence : sequences) {
                    if (sequence == 0) {
                        pd.server.handleReply(reply.withSequence(0));
                    } else {
                        pd.provideReply(reply.withSequence(sequence));
                    }
                }
            }
        });
    }

    private void errorToPlugin(final PackageInfo info, final Exception e) throws StopProcessException {
        if (e instanceof StopProcessException) {
            throw (StopProcessException) e;
        } else if (e instanceof GatewayException) {
            sendFutureException((GatewayException) e);
        } else {
            sendFutureException(new GatewayException((DataMessage) info.getMessage(), Reason.INTERNAL_ERROR));
        }
        // Always throws an exception so processing is stopped
        throw new StopProcessException();
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
                    iccObserving.remove(e.getKey());
                    future.cancel(true);
                    return true;
                }
            }

            return false;
        });
    }
}
