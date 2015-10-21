package br.ufs.gothings.gateway;

import br.ufs.gothings.core.Settings;
import br.ufs.gothings.core.common.GatewayException;
import br.ufs.gothings.core.common.Reason;
import br.ufs.gothings.core.message.*;
import br.ufs.gothings.core.plugin.*;
import br.ufs.gothings.gateway.InterconnectionController.ObserveList;
import br.ufs.gothings.gateway.common.Controller;
import br.ufs.gothings.gateway.common.Package;
import br.ufs.gothings.gateway.common.Sequencer;
import br.ufs.gothings.gateway.common.StopProcessException;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.*;

/**
 * @author Wagner Macedo
 */
public class CommunicationManager {
    private static final Logger logger = LogManager.getFormatterLogger(CommunicationManager.class);

    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    private final ThreadGroup pluginsGroup = new ThreadGroup("GW-plugins");

    private final Sequencer sequencer = new Sequencer();
    private final Map<String, PluginData> pluginsMap = new ConcurrentHashMap<>();
    private final Map<Long, CompletableReply> waitingReplies = new ConcurrentHashMap<>();

    private final Controller inputC;
    private final Controller interConnC;
    private final Controller outputC;
    private final ObserveList iccObserving;

    CommunicationManager() {
        // PackageFactory configuration
        inputC = new InputController();
        interConnC = new InterconnectionController();
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
                final CompletableReply future = waitingReplies.remove(sequence);
                if (future != null) {
                    future.complete(GwReply.EMPTY.withSequence(sequence));
                }
            }

            @Override
            public void send(final GwReply reply) {
                final Package pkg = new Package();
                pkg.setMessage(reply);
                pkg.setSourceProtocol(protocol);
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
                    request.setSequence(sequencer.nextNormal());
                    break;
                case OBSERVE:
                case UNOBSERVE:
                    request.setSequence(0);
                    break;
            }

            final Package pkg = new Package();
            pkg.setMessage(request);
            pkg.setSourceProtocol(protocol);
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
                pluginThread = new Thread(pluginsGroup, () -> {
                    pd.clientExecutor = newExecutorService("GW-PluginClient-" + pd.getProtocol());
                    pd.serverExecutor = newExecutorService("GW-PluginServer-" + pd.getProtocol());
                    pd.client.start();
                });
            } else {
                pluginThread = new Thread(pluginsGroup, () -> {
                    if (pd.client != null) {
                        pd.clientExecutor = newExecutorService("GW-PluginClient-" + pd.getProtocol());
                        pd.client.start();
                    }
                    if (pd.server != null) {
                        pd.serverExecutor = newExecutorService("GW-PluginServer-" + pd.getProtocol());
                        pd.server.start();
                    }
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

    private static ExecutorService newExecutorService(final String name) {
        return Executors.newSingleThreadExecutor(new BasicThreadFactory.Builder()
                .namingPattern(name)
                .build());
    }

    public void stop() {
        // don't continue if stop was already called
        synchronized (timer) {
            if (timer.isShutdown()) return;
        }

        timer.shutdown();

        final Iterator<PluginData> it = pluginsMap.values().iterator();
        while (it.hasNext()) {
            final PluginData pd = it.next();
            it.remove();
            logger.info("stopping %s plugin", pd.getProtocol());

            if (pd.client != null) {
                pd.clientExecutor.execute(() -> {
                    pd.client.stop();
                    pd.client = null;
                });
                pd.clientExecutor.shutdown();
                pd.clientExecutor = null;
            }

            if (pd.server != null) {
                pd.serverExecutor.execute(() -> {
                    pd.server.stop();
                    pd.server = null;
                });
                pd.serverExecutor.shutdown();
                pd.serverExecutor = null;
            }
        }

        pluginsGroup.interrupt();
    }

    private void processRequest(final Package pkg) {
        // Input controller processing
        try {
            inputC.process(pkg);
        } catch (Exception e) {
            errorToPlugin(pkg, e);
        }
        // Interconnection controller processing
        final GwMessage message = pkg.getMessage();
        try {
            interConnC.process(pkg);
        } catch (Exception e) {
            errorToPlugin(pkg, e);
        }
        // If ICC left a request, then it's a work for a plugin
        if (message instanceof GwRequest) {
            final GwRequest request = (GwRequest) message;
            if (!requestToPlugin(request.readOnly(), pkg.getTargetProtocol())) {
                sendFutureException(new GatewayException(request, Reason.UNAVAILABLE_PLUGIN));
            }
        }
        // On the other hand, if ICC left a reply, then pass to OC to continue processing
        else if (message instanceof GwReply) {
            final GwReply reply = (GwReply) message;
            try {
                outputC.process(pkg);
                replyToPlugin(reply.readOnly(), pkg.getReplyTo());
            } catch (Exception e) {
                errorToPlugin(pkg, e);
            }
        }
    }

    private void processReply(final Package pkg) {
        final GwReply reply = (GwReply) pkg.getMessage();

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
        replyToPlugin(reply.readOnly(), pkg.getReplyTo());
    }

    private boolean requestToPlugin(final GwRequest request, final String targetProtocol) {
        final PluginData pd = pluginsMap.get(targetProtocol);
        if (pd != null && pd.client != null) {
            pd.clientExecutor.execute(() -> pd.client.handleRequest(request));
            return true;
        }
        return false;
    }

    private void replyToPlugin(final GwReply reply, final Map<String, long[]> replyTo) {
        replyTo.forEach((protocol, sequences) -> {
            final PluginData pd = pluginsMap.get(protocol);
            if (pd.server != null) {
                pd.serverExecutor.execute(() -> {
                    for (final long sequence : sequences) {
                        if (sequence == 0) {
                            pd.server.handleReply(reply.withSequence(0));
                        } else {
                            pd.provideReply(reply.withSequence(sequence));
                        }
                    }
                });
            }
        });
    }

    private void errorToPlugin(final Package pkg, final Exception e) throws StopProcessException {
        if (e instanceof StopProcessException) {
            throw (StopProcessException) e;
        } else if (e instanceof GatewayException) {
            sendFutureException((GatewayException) e);
        } else {
            sendFutureException(new GatewayException((DataMessage) pkg.getMessage(), Reason.INTERNAL_ERROR));
        }
        // Always throws an exception so processing is stopped
        throw new StopProcessException();
    }

    private class PluginData {
        private final String protocol;

        private PluginClient client;
        private ExecutorService clientExecutor;

        private PluginServer server;
        private ExecutorService serverExecutor;

        private PluginData(final String protocol) {
            this.protocol = protocol;
        }

        public FutureReply addFuture(final GwRequest request) {
            final CompletableReply future = new CompletableReply();
            waitingReplies.put(request.getSequence(), future);
            return future;
        }

        public void provideReply(final GwReply reply) {
            try {
                final CompletableReply future = waitingReplies.remove(reply.getSequence());
                future.complete(reply);
            } catch (NullPointerException e) {
                logger.error("not found a message with sequence %d to send the reply", reply.getSequence());
            }
        }

        public String getProtocol() {
            return protocol;
        }
    }

    private static class CompletableReply implements FutureReply {
        private volatile Instant threshold = Instant.now();
        private volatile CompletableFuture<GwReply> future;

        public CompletableReply() {
            future = new CompletableFuture<>();
        }

        @Override
        public GwReply get() throws InterruptedException, ExecutionException {
            // far future threshold as we don't know how much time is spent waiting here
            threshold = Instant.MAX;
            try {
                return future.get();
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
            return future.get(timeout, unit);
        }

        public boolean complete(final GwReply value) {
            return future.complete(value);
        }

        public boolean completeExceptionally(final Throwable ex) {
            return future.completeExceptionally(ex);
        }

        public int getNumberOfDependents() {
            return future.getNumberOfDependents();
        }

        @Override
        public boolean cancel(final boolean mayInterruptIfRunning) {
            return future.cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean isCancelled() {
            return future.isCancelled();
        }

        @Override
        public boolean isDone() {
            return future.isDone();
        }

        @Override
        public void setListener(final ReplyListener replyListener) {
            throw new UnsupportedOperationException();
        }
    }

    private void sendFutureException(final GatewayException gatewayException) {
        final CompletableReply future = waitingReplies.remove(gatewayException.getErrorMessage().getSequence());
        if (future != null) {
            future.completeExceptionally(gatewayException);
        }
    }

    private void sweepWaitingReplies() {
        waitingReplies.entrySet().removeIf(e -> {
            final CompletableReply future = e.getValue();

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
