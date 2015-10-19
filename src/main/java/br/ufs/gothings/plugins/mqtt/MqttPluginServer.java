package br.ufs.gothings.plugins.mqtt;

import br.ufs.gothings.core.Settings;
import br.ufs.gothings.core.message.GwHeaders;
import br.ufs.gothings.core.message.GwRequest;
import br.ufs.gothings.core.message.headers.Operation;
import br.ufs.gothings.core.plugin.RequestLink;
import org.eclipse.moquette.interception.InterceptHandler;
import org.eclipse.moquette.interception.messages.*;
import org.eclipse.moquette.proto.messages.PublishMessage;
import org.eclipse.moquette.server.config.IConfig;
import org.eclipse.moquette.server.netty.NettyAcceptor;
import org.eclipse.moquette.spi.impl.ProtocolProcessor;
import org.eclipse.moquette.spi.impl.SimpleMessaging;
import org.eclipse.moquette.spi.impl.events.PubAckEvent;
import org.eclipse.moquette.spi.impl.security.IAuthorizator;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.eclipse.moquette.commons.Constants.*;

/**
 * @author Wagner Macedo
 */
public class MqttPluginServer {
    private final RequestLink requestLink;
    private final Settings settings;

    private Runnable stopAction;

    MqttPluginServer(final RequestLink requestLink, final Settings settings) {
        this.requestLink = requestLink;
        this.settings = settings;
    }

    void start() {
        System.out.println(requestLink);
        final SimpleMessaging messaging = SimpleMessaging.getInstance();
        final MoquetteConfig config = new MoquetteConfig();
        config.setProperty(PORT_PROPERTY_NAME, String.valueOf(settings.get(Settings.SERVER_PORT)));

        final ProtocolProcessor processor = messaging.init(config);

        // Hacking moquette with reflection to achieve my goals.
        // NOTE: I'll try, in the future, to put native support for some of these hacks.
        hacksMoquette(messaging, processor, requestLink);

        final NettyAcceptor acceptor = new NettyAcceptor();
        try {
            acceptor.initialize(processor, config);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        stopAction = () -> {
            acceptor.close();
            messaging.shutdown();
        };
    }

    void stop() {
        stopAction.run();
    }

    /**
     *  Prepare moquette intercept handler for use.
     */
    private static void hacksMoquette(final SimpleMessaging messaging,
                                      final ProtocolProcessor processor,
                                      final RequestLink requestLink)
    {
        final Object m_interceptor = getFieldValue(messaging, "m_interceptor");
        final List<InterceptHandler> handlers = getFieldValue(m_interceptor, "handlers");
        final MoquetteIntercept moquetteIntercept = (MoquetteIntercept) handlers.get(0);

        // add reference for the RequestLink
        moquetteIntercept.requestLink = requestLink;

        // add proxy for forbidden methods in the ProtocolProcessor
        moquetteIntercept.processorProxy = new ProtocolProcessorProxy(processor);
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
        @Override
        public boolean canWrite(final String topic, final String user, final String client) {
            /* always false because the real publishing is done by MoquetteIntercept#onDenyPublish */
            return false;
        }

        @Override
        public boolean canRead(final String topic, final String user, final String client) {
            return true;
        }
    }

    public static final class MoquetteIntercept implements InterceptHandler {
        private RequestLink requestLink;
        private ProtocolProcessorProxy processorProxy;

        private final Map<String, ClientInfo> clients = new ConcurrentHashMap<>();
        private final Map<String, SubscriptionInfo> allSubscriptions = new ConcurrentHashMap<>();

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
                    h.setOperation(Operation.OBSERVE);
                    h.setQoS(msg.getRequestedQos().byteValue());
                    h.setPath(msg.getTopicFilter());

                    requestLink.send(request);
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
                    h.setOperation(Operation.UNOBSERVE);
                    h.setPath(msg.getTopicFilter());

                    requestLink.send(request);
                }
            }
        }

        @Override
        public void onDenyPublish(final InterceptPublishMessage msg) {
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
            requestLink.send(request);

            // send confirmation to the external client
            final byte qos = msg.getQos().byteValue();
            if (qos > 0) {
                // hack to get message id
                final PublishMessage realMsg = getFieldValue(msg, "msg");
                if (qos == 1) {
                    processorProxy.sendPubAck(msg.getClientID(), realMsg.getMessageID());
                } else if (qos == 2) {
                    processorProxy.sendPubRec(msg.getClientID(), realMsg.getMessageID());
                }
            }
        }
    }

    private static final class ProtocolProcessorProxy {
        private final ProtocolProcessor processor;

        private final Method p_sendPubAck;
        private final Method p_sendPubRec;

        public ProtocolProcessorProxy(final ProtocolProcessor processor) {
            this.processor = processor;
            p_sendPubAck = getMethod(processor, "sendPubAck", PubAckEvent.class);
            p_sendPubRec = getMethod(processor, "sendPubRec", String.class, int.class);
        }

        public void sendPubAck(final String clientID, final int messageID) {
            invoke(p_sendPubAck, new PubAckEvent(messageID, clientID));
        }

        public void sendPubRec(final String clientID, final int messageID) {
            invoke(p_sendPubRec, clientID, messageID);
        }

        private void invoke(final Method method, final Object... args) {
            try {
                method.invoke(processor, args);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
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
}
