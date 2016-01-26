package br.ufs.gothings.plugins.mqtt;

import br.ufs.gothings.core.common.ErrorCode;
import br.ufs.gothings.core.message.GwError;
import br.ufs.gothings.core.message.headers.GwHeaders;
import br.ufs.gothings.core.message.GwReply;
import br.ufs.gothings.core.message.GwRequest;
import br.ufs.gothings.core.message.headers.Operation;
import br.ufs.gothings.core.plugin.ReplyLink;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.paho.client.mqttv3.*;

import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static br.ufs.gothings.core.message.headers.HeaderNames.*;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static org.eclipse.paho.client.mqttv3.MqttException.*;

/**
 * @author Wagner Macedo
 */
public final class MqttPluginClient {
    private static final Logger logger = LogManager.getFormatterLogger(MqttPluginClient.class);

    private final Map<String, MqttClientConnection> connections;
    private final ReplyLink replyLink;
    private volatile boolean closed = false;

    MqttPluginClient(final ReplyLink replyLink) {
        this.replyLink = replyLink;
        connections = new ConcurrentHashMap<>();
    }

    void sendRequest(final GwRequest request) {
        if (!closed) {
            final String host = request.headers().get(GW_TARGET);
            try {
                final MqttClientConnection conn = getMqttConnection(host);
                conn.sendMessage(request);
            } catch (MqttException e) {
                switch (e.getReasonCode()) {
                    case REASON_CODE_CLIENT_EXCEPTION:
                        if (!(e.getCause() instanceof UnknownHostException)) {
                            break;
                        }
                    case REASON_CODE_SERVER_CONNECT_ERROR:
                    case REASON_CODE_BROKER_UNAVAILABLE:
                    case REASON_CODE_CLIENT_TIMEOUT:
                    case REASON_CODE_CONNECTION_LOST:
                        replyLink.sendError(new GwError(request, ErrorCode.TARGET_NOT_FOUND));
                        break;
                }
                replyLink.sendError(new GwError(request, ErrorCode.OTHER));
            }
        } else {
            replyLink.sendError(new GwError(request, ErrorCode.OTHER));
        }
    }

    /**
     * Close all connections and stop to receive requests
     */
    void close() {
        closed = true;
        connections.forEach((host, connection) -> {
            try {
                connection.client.disconnect().waitForCompletion();
            } catch (MqttException e) {
                logger.warn("problem disconnecting %s", host);
            }
        });
    }

    private MqttClientConnection getMqttConnection(final String host) throws MqttException {
        try {
            return connections.computeIfAbsent(host, k -> new MqttClientConnection(host, replyLink));
        } catch (RuntimeException e) {
            if (e.getCause() instanceof MqttException) {
                throw ((MqttException) e.getCause());
            } else {
                throw e;
            }
        }
    }

    private static final class MqttClientConnection {
        private final String host;
        private final ReplyLink replyLink;

        private final MqttAsyncClient client;
        private final SubscriptionControl control = new SubscriptionControl();

        public MqttClientConnection(final String host, final ReplyLink replyLink) throws RuntimeException {
            this.host = host;
            this.replyLink = replyLink;
            try {
                client = new MqttAsyncClient("tcp://" + host, "gothings-client_" + host.hashCode());
                client.setCallback(new MqttClientCallback(this));
                final IMqttToken connectToken = client.connect(new MqttConnectOptions());
                connectToken.waitForCompletion();
            } catch (MqttException e) {
                throw new RuntimeException(e);
            }
        }

        public void sendMessage(GwRequest msg) throws MqttException {
            final GwHeaders h = msg.headers();
            final Operation operation = h.get(GW_OPERATION);
            final String topic = h.get(GW_PATH);
            final int qos = max(0, min(2, h.get(GW_QOS)));
            switch (operation) {
                // CREATE or UPDATE is mapped as a publish, but this plugin treats CREATE as a retained message
                case CREATE:
                case UPDATE:
                    final MqttMessage mqttMessage = new MqttMessage(msg.payload().asBytes());
                    mqttMessage.setRetained(operation == Operation.CREATE);
                    mqttMessage.setQos(qos);
                    client.publish(topic, mqttMessage);
                    replyLink.ack(msg.getSequence());
                    break;

                // MQTT doesn't really specify a delete operation, but when a retained message with a zero byte payload
                // is sent, the broker removes the retained message
                case DELETE:
                    client.publish(topic, ArrayUtils.EMPTY_BYTE_ARRAY, 0, true);
                    replyLink.ack(msg.getSequence());
                    break;

                // READ and OBSERVE are unsurprisingly directly mapped to subscribe
                case READ:
                case OBSERVE:
                    synchronized (control) {
                        final boolean marked = control.mark(topic, operation);
                        if (!marked) {
                            client.subscribe(topic, qos);
                            logger.trace("subscribed on topic %s", topic);
                        }
                    }
                    break;

                // UNOBSERVE is the equivalent of unsubscribe
                case UNOBSERVE:
                    synchronized (control) {
                        control.remove(topic);
                    }
                    client.unsubscribe(topic);
                    logger.trace("unsubscribed from topic %s", topic);
                    break;
            }
        }

    }

    private static final class MqttClientCallback implements MqttCallback {
        private final MqttClientConnection mc;

        public MqttClientCallback(final MqttClientConnection mc) {
            this.mc = mc;
        }

        // TODO: try to reconnect on fail
        @Override
        public void connectionLost(Throwable cause) {

        }

        @Override
        public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception {
            synchronized (mc.control) {
                final boolean marked = mc.control.unmarkWaitRead(topic);
                if (!marked) {
                    mc.client.unsubscribe(topic);
                    logger.trace("unsubscribed from topic %s due to no more waiters", topic);
                }

                final GwReply msg = new GwReply();
                msg.payload().set(mqttMessage.getPayload());
                final GwHeaders h = msg.headers();
                h.set(GW_TARGET, mc.host);
                h.set(GW_PATH, topic);

                mc.replyLink.send(msg);
            }
        }

        // TODO: what to do here?
        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {

        }
    }

    private static final class SubscriptionControl {
        private final Map<String, Integer> table = new HashMap<>();

        private static final byte WAIT_READ = 0b01;
        private static final byte SUBSCRIBE = 0b10;

        public boolean mark(final String topic, final Operation operation) {
            return mark(normalize(topic), operation == Operation.READ ? WAIT_READ : SUBSCRIBE);
        }

        private boolean mark(final String topic, final byte newMark) {
            final Integer oldMark = table.getOrDefault(topic, 0);
            try {
                return oldMark > 0;
            } finally {
                table.put(topic, newMark | oldMark);
            }
        }

        public boolean unmarkWaitRead(final String topic) {
            final Integer computeMark = table.compute(normalize(topic), (k, mark) -> {
                final int newMark = mark ^ WAIT_READ;
                return newMark == 0 ? null : newMark;
            });
            return computeMark != null;
        }

        public void remove(final String topic) {
            table.remove(normalize(topic));
        }

        private static String normalize(final String topic) {
            return topic.replaceFirst("^/+", "");
        }
    }
}
