package br.ufs.gothings.plugins.mqtt;

import br.ufs.gothings.core.GwHeaders;
import br.ufs.gothings.core.GwMessage;
import br.ufs.gothings.core.message.GwNews;
import br.ufs.gothings.core.message.GwRequest;
import br.ufs.gothings.core.message.headers.Operation;
import br.ufs.gothings.core.message.sink.MessageLink;
import br.ufs.gothings.core.message.sink.MessageListener;
import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.paho.client.mqttv3.*;

import java.util.HashMap;
import java.util.Map;

import static org.apache.commons.lang3.ObjectUtils.max;
import static org.apache.commons.lang3.ObjectUtils.min;

/**
 * @author Wagner Macedo
 */
public final class MqttPluginClient {
    private final Map<String, MqttConnection> connections;
    private final MessageLink messageLink;

    public MqttPluginClient(final MessageLink messageLink) {
        this.messageLink = messageLink;
        this.messageLink.setUp(new MessageSinkListener());
        connections = new HashMap<>();
    }

    private class MessageSinkListener implements MessageListener {
        @Override
        public void valueReceived(GwMessage msg) throws MqttException {
            final String host = msg.headers().targetsHeader().get(0);

            final MqttConnection conn = getMqttConnection(host);
            conn.sendMessage((GwRequest) msg);
        }
    }

    private MqttConnection getMqttConnection(final String host) throws MqttException {
        final MqttConnection conn = connections.get(host);
        if (conn != null) {
            return conn;
        }

        return new MqttConnection(host);
    }

    private class MqttConnection {
        private final MqttAsyncClient client;
        private final IMqttToken connectionToken;

        public MqttConnection(String host) throws MqttException {
            client = new MqttAsyncClient("tcp://" + host, "gothings-client_" + host.hashCode());
            client.setCallback(new MqttCallback() {
                // TODO: try to reconnect on fail
                @Override
                public void connectionLost(Throwable cause) {

                }

                @Override
                public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception {
                    final GwNews msg = new GwNews();
                    msg.payload().set(mqttMessage.getPayload());
                    final GwHeaders h = msg.headers();
                    h.targetsHeader().add(host);
                    h.pathHeader().set(topic);

                    messageLink.broadcast(msg);
                }

                // TODO: what to do here?
                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {

                }
            });
            connectionToken = client.connect(new MqttConnectOptions());
        }

        public void sendMessage(GwRequest msg) throws MqttException {
            connectionToken.waitForCompletion();
            final GwHeaders h = msg.headers();
            final Operation operation = h.operationHeader().get();
            final String topic = h.pathHeader().get();
            final int qos = max(0, min(2, h.qosHeader().get()));
            switch (operation) {
                // CREATE or UPDATE is mapped as a publish, but this plugin treats CREATE as a retained message
                case CREATE:
                case UPDATE:
                    final MqttMessage mqttMessage = new MqttMessage(msg.payload().asBytes());
                    mqttMessage.setRetained(operation == Operation.CREATE);
                    mqttMessage.setQos(qos);
                    client.publish(topic, mqttMessage);
                    break;

                // MQTT doesn't really specify a delete operation, but when a retained message with a zero byte payload
                // is sent, the broker removes the retained message
                case DELETE:
                    client.publish(topic, ArrayUtils.EMPTY_BYTE_ARRAY, 0, true);
                    break;

                // READ is unsurprisingly directly mapped to subscribe
                case READ:
                    client.subscribe(topic, qos);
                    break;
            }
        }
    }
}
