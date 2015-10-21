package br.ufs.gothings.plugins.mqtt;

import br.ufs.gothings.core.Settings;
import br.ufs.gothings.core.message.GwError;
import br.ufs.gothings.core.message.GwHeaders;
import br.ufs.gothings.core.message.GwReply;
import br.ufs.gothings.core.message.GwRequest;
import br.ufs.gothings.core.message.headers.Operation;
import br.ufs.gothings.core.plugin.RequestLink;
import io.netty.util.AttributeKey;
import org.apache.commons.lang3.RandomStringUtils;
import org.eclipse.moquette.interception.InterceptHandler;
import org.eclipse.moquette.interception.messages.*;
import org.eclipse.moquette.parser.netty.Utils;
import org.eclipse.moquette.proto.messages.AbstractMessage;
import org.eclipse.moquette.proto.messages.ConnAckMessage;
import org.eclipse.moquette.proto.messages.ConnectMessage;
import org.eclipse.moquette.proto.messages.PublishMessage;
import org.eclipse.moquette.server.ServerChannel;
import org.eclipse.moquette.server.config.IConfig;
import org.eclipse.moquette.server.netty.NettyAcceptor;
import org.eclipse.moquette.spi.impl.ProtocolProcessor;
import org.eclipse.moquette.spi.impl.SimpleMessaging;
import org.eclipse.moquette.spi.impl.events.PubAckEvent;
import org.eclipse.moquette.spi.impl.security.IAuthorizator;
import org.eclipse.moquette.spi.impl.subscriptions.SubscriptionsStore;

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

import static org.eclipse.moquette.commons.Constants.*;

/**
 * @author Wagner Macedo
 */
public class MqttPluginServer {
    // Extreme unique client id (maximum length random ascii)
    private static final String EMBEDDED_CLIENT_ID = RandomStringUtils.randomAscii(65535);

    private final Shared shared;
    private final Settings settings;

    private final AtomicInteger messageIdGen = new AtomicInteger(0);
    private Runnable stopAction = () -> {};

    private EmbeddedChannel embeddedChannel;

    MqttPluginServer(final RequestLink requestLink, final Settings settings) {
        this.shared = new Shared(requestLink);
        this.settings = settings;
    }

    void start() {
        // Get messaging instance and set moquette port
        final SimpleMessaging messaging = SimpleMessaging.getInstance();
        final MoquetteConfig config = new MoquetteConfig();
        config.setProperty(PORT_PROPERTY_NAME, String.valueOf(settings.get(Settings.SERVER_PORT)));

        // Initiate the processor and instantiate the acceptor
        final ProtocolProcessor processor = messaging.init(config);
        final NettyAcceptor acceptor = new NettyAcceptor();

        // Define the stop instructions
        stopAction = () -> {
            try {
                acceptor.close();
            } finally {
                messaging.shutdown();
                shared.release();
                embeddedChannel = null;
            }
        };

        // Hacking moquette with reflection to achieve my goals.
        // NOTE: I'll try, in the future, to put native support for some of these hacks.
        hacksMoquette(shared, processor);

        // Connect the embedded channel
        embeddedChannel = new EmbeddedChannel(stopAction);
        processor.processConnect(embeddedChannel, buildConnectMessage());
        if (embeddedChannel.getReturnCode() != ConnAckMessage.CONNECTION_ACCEPTED) {
            stopAction.run();
            return;
        }

        try {
            acceptor.initialize(processor, config);
        } catch (IOException e) {
            stopAction.run();
        }
    }

    private static ConnectMessage buildConnectMessage() {
        final ConnectMessage connectMessage = new ConnectMessage();
        connectMessage.setClientID(EMBEDDED_CLIENT_ID);
        connectMessage.setUserFlag(false);
        connectMessage.setWillFlag(false);
        connectMessage.setCleanSession(true);
        connectMessage.setProtocolVersion(Utils.VERSION_3_1_1);
        return connectMessage;
    }

    void stop() {
        stopAction.run();
    }

    public void receiveReply(final GwReply reply) {
        final PublishMessage msg = new PublishMessage();
        msg.setMessageID(messageIdGen.incrementAndGet());
        msg.setRetainFlag(false);
        msg.setPayload(reply.payload().asBuffer());
        final GwHeaders h = reply.headers();
        msg.setTopicName(h.getPath());
        msg.setQos(AbstractMessage.QOSType.MOST_ONE);

        shared.processorProxy.processPublish(embeddedChannel, msg);
    }

    public void receiveError(final GwError error) {
        switch (error.getReason()) {
            // If target plugin is unavailable then, as we know the gateway cannot register another plugin dynamically,
            // silently remove all subscribed clients from this topic and avoid future clients to subscribe to it.
            case UNAVAILABLE_PLUGIN:
            // The same action is applied for the following unrecoverable errors.
            case INVALID_URI:
            case OTHER:
            case INTERNAL_ERROR:
                // Instruct the authorizator to don't accept this topic anymore
                final String topic = error.headers().getPath();
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
                shared.delayer.schedule(() -> shared.requestLink.send(request), 30, TimeUnit.SECONDS);
                break;
        }
    }

    /**
     *  Prepare moquette intercept handler for use.
     */
    private static void hacksMoquette(final Shared shared, final ProtocolProcessor processor)
    {
        final Object m_interceptor = getFieldValue(processor, "m_interceptor");
        final List<InterceptHandler> handlers = getFieldValue(m_interceptor, "handlers");
        final MoquetteIntercept moquetteIntercept = (MoquetteIntercept) handlers.get(0);

        // add proxy for forbidden methods in the ProtocolProcessor
        shared.processorProxy = new ProtocolProcessorProxy(processor);

        // add reference for the authorizator forbiddenTopics map
        final MoquetteAuthorizator authorizator = getFieldValue(processor, "m_authorizator");
        shared.forbiddenTopics = authorizator.forbiddenTopics;

        // add reference for the registered clients
        shared.clients = moquetteIntercept.clients;

        // add to the intercept handler a reference for the shared plugin data
        moquetteIntercept.shared = shared;
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
            put(AUTHORIZATOR_CLASS_NAME, MoquetteAuthorizator.class.getName());
            put("intercept.handler", MoquetteIntercept.class.getName());
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

        @Override
        public boolean canWrite(final String topic, final String user, final String client) {
            /* only the embedded client id is allowed to publish */
            return Objects.equals(EMBEDDED_CLIENT_ID, client);
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

        @Override
        public void onConnect(final InterceptConnectMessage msg) {
            // Ignore embedded client id
            if (Objects.equals(EMBEDDED_CLIENT_ID, msg.getClientID())) return;

            // get or create an entry with this clientID
            final ClientInfo client = clients.computeIfAbsent(msg.getClientID(),
                    k -> new ClientInfo(msg.isCleanSession()));
            // remove old subscriptions if connection is a clean session
            if (client.cleanSession)
                client.removeSubscriptions();
        }

        @Override
        public void onDisconnect(final InterceptDisconnectMessage msg) {
            // Ignore embedded client id
            if (Objects.equals(EMBEDDED_CLIENT_ID, msg.getClientID())) return;

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
            // Ignore embedded client id
            if (Objects.equals(EMBEDDED_CLIENT_ID, msg.getClientID())) return;

            final ClientInfo client = clients.get(msg.getClientID());
            if (client != null) {
                final SubscriptionInfo subscription = getSubscription(msg.getTopicFilter());
                final long counter = client.addSubscription(subscription);

                // if subscribing counter == 1 then makes an internal request
                if (counter == 1) {
                    final GwRequest request = new GwRequest();
                    final GwHeaders h = request.headers();
                    h.setOperation(Operation.OBSERVE);
                    h.setQoS(msg.getRequestedQos().byteValue());
                    h.setPath(msg.getTopicFilter());

                    shared.requestLink.send(request);
                }
            }
        }

        private SubscriptionInfo getSubscription(final String topic) {
            return allSubscriptions.computeIfAbsent(topic, k -> new SubscriptionInfo(topic));
        }

        @Override
        public void onUnsubscribe(final InterceptUnsubscribeMessage msg) {
            // Ignore embedded client id
            if (Objects.equals(EMBEDDED_CLIENT_ID, msg.getClientID())) return;

            final ClientInfo client = clients.get(msg.getClientID());
            if (client != null) {
                // remove from the list of subscriptions of this clientID
                final SubscriptionInfo subscription = client.removeSubscription(msg.getTopicFilter());

                // if subscribing counter == 0 request gateway with operation=UNOBSERVE
                if (subscription.getCounter() == 0) {
                    final GwRequest request = new GwRequest();
                    final GwHeaders h = request.headers();
                    h.setOperation(Operation.UNOBSERVE);
                    h.setPath(msg.getTopicFilter());

                    shared.requestLink.send(request);
                }
            }
        }

        @Override
        public void onDenyPublish(final InterceptPublishMessage msg) {
            // Ignore embedded client id
            if (Objects.equals(EMBEDDED_CLIENT_ID, msg.getClientID())) return;

            // send internal request with operation=CREATE, operation=UPDATE or operation=DELETE
            final GwRequest request = new GwRequest();
            final GwHeaders h = request.headers();
            h.setQoS(msg.getQos().byteValue());
            h.setPath(msg.getTopicName());

            // when retain=true request with UPDATE (if has payload) or DELETE (if empty payload)
            if (msg.isRetainFlag()) {
                h.setOperation(msg.getPayload().hasRemaining() ? Operation.UPDATE : Operation.DELETE);
            }
            // when retain=false request with CREATE
            else {
                h.setOperation(Operation.CREATE);
            }

            // the internal request
            shared.requestLink.send(request);

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

    private static final class EmbeddedChannel implements ServerChannel {
        private final Runnable stopAction;

        private final Map<Object, Object> attributes = new ConcurrentHashMap<>();
        private byte returnCode;

        public EmbeddedChannel(final Runnable stopAction) {
            this.stopAction = stopAction;
        }

        @Override
        public Object getAttribute(final AttributeKey<Object> key) {
            return attributes.get(key);
        }

        @Override
        public void setAttribute(final AttributeKey<Object> key, final Object value) {
            attributes.put(key, value);
        }

        @Override
        public void setIdleTime(final int idleTime) {
            // not needed on this implementation
        }

        @Override
        public void close(final boolean immediately) {
            stopAction.run();
        }

        @Override
        public void write(final Object value) {
            try {
                final AbstractMessage msg = (AbstractMessage) value;
                if (msg instanceof ConnAckMessage) {
                    returnCode = ((ConnAckMessage) msg).getReturnCode();
                }
            } catch (RuntimeException ignored) {
            }
        }

        public byte getReturnCode() {
            return returnCode;
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
            p_sendPubAck = getMethod(processor, "sendPubAck", PubAckEvent.class);
            p_sendPubRec = getMethod(processor, "sendPubRec", String.class, int.class);
            p_sendPubComp = getMethod(processor, "sendPubComp", String.class, int.class);
            // server internals
            subscriptions = getFieldValue(processor, "subscriptions");
        }

        public void sendPubAck(final String clientID, final int messageID) {
            invoke(p_sendPubAck, new PubAckEvent(messageID, clientID));
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

        public void processPublish(final EmbeddedChannel embeddedChannel, final PublishMessage msg) {
            processor.processPublish(embeddedChannel, msg);
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
        private final RequestLink requestLink;
        private final ScheduledExecutorService delayer = Executors.newSingleThreadScheduledExecutor();

        private ProtocolProcessorProxy processorProxy;
        private Set<String> forbiddenTopics;
        private Map<String, ClientInfo> clients;

        private Shared(final RequestLink requestLink) {
            this.requestLink = requestLink;
        }

        private void release() {
            processorProxy = null;
            forbiddenTopics = null;
            clients = null;
        }
    }
}
