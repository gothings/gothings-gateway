package br.ufs.gothings.plugins.mqtt;

import br.ufs.gothings.core.Settings;
import br.ufs.gothings.core.message.GwError;
import br.ufs.gothings.core.message.headers.GwHeaders;
import br.ufs.gothings.core.message.GwReply;
import br.ufs.gothings.core.message.GwRequest;
import br.ufs.gothings.core.message.headers.Operation;
import br.ufs.gothings.core.plugin.ReplyListener;
import br.ufs.gothings.core.plugin.RequestLink;
import io.moquette.interception.InterceptHandler;
import io.moquette.interception.messages.*;
import io.moquette.proto.messages.AbstractMessage;
import io.moquette.proto.messages.PublishMessage;
import io.moquette.server.Server;
import io.moquette.server.config.IConfig;
import io.moquette.spi.impl.ProtocolProcessor;
import io.moquette.spi.security.IAuthorizator;
import io.moquette.spi.impl.subscriptions.SubscriptionsStore;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static br.ufs.gothings.core.message.headers.HeaderNames.*;
import static io.moquette.BrokerConstants.*;

/**
 * @author Wagner Macedo
 */
public class MqttPluginServer {
    private final Shared shared;
    private final Settings settings;

    private final AtomicInteger messageIdGen = new AtomicInteger(0);

    private Server embeddedServer;

    MqttPluginServer(final RequestLink requestLink, final Settings settings) {
        this.shared = new Shared(this, requestLink);
        this.settings = settings;
    }

    void start() {
        // Instantiate moquette server
        embeddedServer = new Server();

        // Create config and set moquette port
        final MoquetteConfig config = new MoquetteConfig();
        config.setProperty(PORT_PROPERTY_NAME, String.valueOf(settings.get(Settings.SERVER_PORT)));

        // The custom InterceptHandler and IAuthorizator used by this plugin
        final InterceptHandler handler = new MoquetteIntercept(shared);
        final IAuthorizator authorizator = new MoquetteAuthorizator(shared);

        try {
            embeddedServer.startServer(config, Collections.singletonList(handler), null, null, authorizator);

            // Hacking moquette with reflection to achieve the plugin goals.
            final ProtocolProcessor processor = getFieldValue(embeddedServer, "m_processor");
            shared.processorProxy = new ProtocolProcessorProxy(processor);
        } catch (IOException e) {
            stop();
        }
    }

    void stop() {
        try {
            embeddedServer.stopServer();
        } finally {
            shared.release();
            embeddedServer = null;
        }
    }

    public void receiveReply(final GwReply reply) {
        final PublishMessage msg = new PublishMessage();
        msg.setMessageID(messageIdGen.incrementAndGet());
        msg.setRetainFlag(false);
        msg.setPayload(reply.payload().asBuffer());
        final GwHeaders h = reply.headers();
        msg.setTopicName(h.get(GW_PATH));
        msg.setQos(AbstractMessage.QOSType.MOST_ONE);

        embeddedServer.internalPublish(msg);
    }

    public void receiveError(final GwError error) {
        switch (error.getCode()) {
            // If target plugin is unavailable then, as we know the gateway cannot register another plugin dynamically,
            // silently remove all subscribed clients from this topic and avoid future clients to subscribe to it.
            case UNAVAILABLE_PLUGIN:
            // The same action is applied for the following unrecoverable errors.
            case INVALID_URI:
            case OTHER:
            case INTERNAL_ERROR:
                // Instruct the authorizator to don't accept this topic anymore
                final String topic = error.headers().get(GW_PATH);
                shared.forbiddenTopics.add(topic);
                // Remove the topics from all the clients
                shared.clients.forEach((clientID, clientInfo) -> {
                    // Remove from plugin data set
                    try {
                        clientInfo.removeSubscription(topic);
                    } catch (NoSuchElementException ignored) {
                    }
                    // Remove from moquette data set
                    shared.processorProxy.removeSubscription(topic, clientID);
                });
                break;
            // The reasons target/path not found don't mean the topic won't be find on next observes, so try again on
            // the future.
            case TARGET_NOT_FOUND:
            case PATH_NOT_FOUND:
                final GwRequest request = new GwRequest(error.headers(), null);
                shared.delayer.schedule(() -> shared.sendRequest(request), 30, TimeUnit.SECONDS);
                break;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T getFieldValue(final Object obj, final String name) {
        try {
            final Field field = obj.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(obj);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static Method getMethod(final Object obj, final String name, final Class<?>... parameterTypes) {
        try {
            final Method method = obj.getClass().getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private static final class MoquetteConfig implements IConfig {
        private final Properties props = new Properties() {{
            put(HOST_PROPERTY_NAME, HOST);
            put(PASSWORD_FILE_PROPERTY_NAME, "");
            put(PERSISTENT_STORE_PROPERTY_NAME, "");
            put(ALLOW_ANONYMOUS_PROPERTY_NAME, "true");
            put(AUTHENTICATOR_CLASS_NAME, "");
        }};

        @Override
        public void setProperty(final String name, final String value) {
            props.setProperty(name, value);
        }

        @Override
        public String getProperty(final String name) {
            return props.getProperty(name);
        }

        @Override
        public String getProperty(final String name, final String defaultValue) {
            return props.getProperty(name, defaultValue);
        }
    }

    public static final class MoquetteAuthorizator implements IAuthorizator {
        private final Set<String> forbiddenTopics = Collections.newSetFromMap(new ConcurrentHashMap<>());

        public MoquetteAuthorizator(final Shared shared) {
            // copy reference of forbiddenTopics set into shared
            shared.forbiddenTopics = this.forbiddenTopics;
        }

        @Override
        public boolean canWrite(final String topic, final String user, final String client) {
            return false;
        }

        @Override
        public boolean canRead(final String topic, final String user, final String client) {
            return !forbiddenTopics.contains(topic);
        }
    }

    public static final class MoquetteIntercept implements InterceptHandler {
        private Shared shared;

        private final Map<String, ClientInfo> clients = new ConcurrentHashMap<>();
        private final Map<String, SubscriptionInfo> allSubscriptions = new ConcurrentHashMap<>();

        public MoquetteIntercept(final Shared shared) {
            // copy reference of shared
            this.shared = shared;
            // copy reference of clients map into shared
            shared.clients = this.clients;
        }

        @Override
        public void onConnect(final InterceptConnectMessage msg) {
            // get or create an entry with this clientID
            final ClientInfo client = clients.computeIfAbsent(msg.getClientID(),
                    k -> new ClientInfo(msg.isCleanSession()));
            // remove old subscriptions if connection is a clean session
            if (client.cleanSession)
                client.removeSubscriptions();
        }

        @Override
        public void onDisconnect(final InterceptDisconnectMessage msg) {
            // remove the entry with this clientID
            final ClientInfo client = clients.remove(msg.getClientID());
            // remove old subscriptions if connection was a clean session
            if (client.cleanSession)
                client.removeSubscriptions();
        }

        @Override
        public void onPublish(final InterceptPublishMessage msg) {/* do nothing */}

        /**
         * The following is done when a subscribing is received:
         *
         * 1. add to the list of subscriptions of this clientID
         * 2. increment the subscribers number of the subscription
         * 3. request gateway with operation=OBSERVE if needed
         */
        @Override
        public void onSubscribe(final InterceptSubscribeMessage msg) {
            final ClientInfo client = clients.get(msg.getClientID());
            if (client != null) {
                final SubscriptionInfo subscription = getSubscription(msg.getTopicFilter());
                final long counter = client.addSubscription(subscription);

                // if subscribing counter == 1 then makes an internal request
                if (counter == 1) {
                    final GwRequest request = new GwRequest();
                    final GwHeaders h = request.headers();
                    h.set(GW_OPERATION, Operation.OBSERVE);
                    h.set(GW_QOS, msg.getRequestedQos().byteValue());
                    h.set(GW_PATH, msg.getTopicFilter());

                    shared.sendRequest(request);
                }
            }
        }

        private SubscriptionInfo getSubscription(final String topic) {
            return allSubscriptions.computeIfAbsent(topic, k -> new SubscriptionInfo(topic));
        }

        @Override
        public void onUnsubscribe(final InterceptUnsubscribeMessage msg) {
            final ClientInfo client = clients.get(msg.getClientID());
            if (client != null) {
                // remove from the list of subscriptions of this clientID
                final SubscriptionInfo subscription = client.removeSubscription(msg.getTopicFilter());

                // if subscribing counter == 0 request gateway with operation=UNOBSERVE
                if (subscription.getCounter() == 0) {
                    final GwRequest request = new GwRequest();
                    final GwHeaders h = request.headers();
                    h.set(GW_OPERATION, Operation.UNOBSERVE);
                    h.set(GW_PATH, msg.getTopicFilter());

                    shared.sendRequest(request);
                }
            }
        }

        @Override
        public void onDenyPublish(final InterceptPublishMessage msg) {
            // send internal request with operation=CREATE, operation=UPDATE or operation=DELETE
            final GwRequest request = new GwRequest();
            final GwHeaders h = request.headers();
            h.set(GW_QOS, msg.getQos().byteValue());
            h.set(GW_PATH, msg.getTopicName());

            // when retain=true request with UPDATE (if has payload) or DELETE (if empty payload)
            if (msg.isRetainFlag()) {
                h.set(GW_OPERATION, msg.getPayload().hasRemaining() ? Operation.UPDATE : Operation.DELETE);
            }
            // when retain=false request with CREATE
            else {
                h.set(GW_OPERATION, Operation.CREATE);
            }

            // the internal request
            shared.sendRequest(request);

            // send confirmation to the external client
            final byte qos = msg.getQos().byteValue();
            if (qos > 0) {
                // hack to get message id
                final PublishMessage realMsg = getFieldValue(msg, "msg");
                if (qos == 1) {
                    shared.processorProxy.sendPubAck(msg.getClientID(), realMsg.getMessageID());
                } else if (qos == 2) {
                    // QoS 2: part 1
                    shared.processorProxy.sendPubRec(msg.getClientID(), realMsg.getMessageID());
                    // QoS 2: part 2 (ugly workaround as moquette doesn't allow to change processor flow)
                    shared.delayer.schedule(
                            () -> shared.processorProxy.sendPubComp(msg.getClientID(), realMsg.getMessageID()),
                            30, TimeUnit.SECONDS);
                }
            }
        }
    }

    private static final class ProtocolProcessorProxy {
        private final ProtocolProcessor processor;

        private final Method p_sendPubAck;
        private final Method p_sendPubRec;
        private final Method p_sendPubComp;
        private final SubscriptionsStore subscriptions;

        public ProtocolProcessorProxy(final ProtocolProcessor processor) {
            this.processor = processor;
            // server to client methods
            p_sendPubAck = getMethod(processor, "sendPubAck", String.class, int.class);
            p_sendPubRec = getMethod(processor, "sendPubRec", String.class, int.class);
            p_sendPubComp = getMethod(processor, "sendPubComp", String.class, int.class);
            // server internals
            subscriptions = getFieldValue(processor, "subscriptions");
        }

        public void sendPubAck(final String clientID, final int messageID) {
            invoke(p_sendPubAck, clientID, messageID);
        }

        public void sendPubRec(final String clientID, final int messageID) {
            invoke(p_sendPubRec, clientID, messageID);
        }

        public void sendPubComp(final String clientID, final Integer messageID) {
            invoke(p_sendPubComp, clientID, messageID);
        }

        private void invoke(final Method method, final Object... args) {
            try {
                method.invoke(processor, args);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }

        public void removeSubscription(final String topic, final String clientID) {
            subscriptions.removeSubscription(topic, clientID);
        }
    }

    private static final class ClientInfo {
        final boolean cleanSession;

        private final List<SubscriptionInfo> subscriptions = new ArrayList<>();
        private final Lock lock = new ReentrantLock();

        public ClientInfo(final boolean cleanSession) {
            this.cleanSession = cleanSession;
        }

        public long addSubscription(final SubscriptionInfo subscription) {
            final int counter = subscription.incrementCounter();
            lock.lock();
            subscriptions.add(subscription);
            lock.unlock();
            return counter;
        }

        public SubscriptionInfo removeSubscription(final String topic) {
            lock.lock();
            try {
                final Iterator<SubscriptionInfo> it = subscriptions.iterator();
                while (it.hasNext()) {
                    final SubscriptionInfo subscription = it.next();
                    if (Objects.equals(subscription.name, topic)) {
                        it.remove();
                        subscription.decrementCounter();
                        return subscription;
                    }
                }
            } finally {
                lock.unlock();
            }
            throw new NoSuchElementException(topic);
        }

        // remove all subscriptions decrementing subscribers counter for each subscription
        public void removeSubscriptions() {
            lock.lock();
            subscriptions.forEach(SubscriptionInfo::decrementCounter);
            subscriptions.clear();
            lock.unlock();
        }
    }

    private static final class SubscriptionInfo {
        final String name;

        private final AtomicInteger counter = new AtomicInteger(0);

        public SubscriptionInfo(final String name) {
            this.name = name;
        }

        public int incrementCounter() {
            return counter.incrementAndGet();
        }

        public int decrementCounter() {
            return counter.decrementAndGet();
        }

        public int getCounter() {
            return counter.get();
        }
    }

    private static final class Shared {
        private final MqttPluginServer pluginServer;
        private final RequestLink requestLink;
        private final ScheduledExecutorService delayer = Executors.newSingleThreadScheduledExecutor();

        private ProtocolProcessorProxy processorProxy;
        private Set<String> forbiddenTopics;
        private Map<String, ClientInfo> clients;

        private Shared(final MqttPluginServer pluginServer, final RequestLink requestLink) {
            this.pluginServer = pluginServer;
            this.requestLink = requestLink;
        }

        private void release() {
            processorProxy = null;
            forbiddenTopics = null;
            clients = null;
        }

        private void sendRequest(final GwRequest request) {
            requestLink.send(request)
                .setListener(new ReplyListener() {
                    @Override
                    public void onReply(final GwReply reply) {
                        pluginServer.receiveReply(reply);
                    }

                    @Override
                    public void onError(final GwError error) {
                        pluginServer.receiveError(error);
                    }
                });
        }
    }
}
