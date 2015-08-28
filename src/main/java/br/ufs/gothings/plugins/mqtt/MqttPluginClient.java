package br.ufs.gothings.plugins.mqtt;

import br.ufs.gothings.core.GwHeaders;
import br.ufs.gothings.core.GwMessage;
import br.ufs.gothings.core.message.Operation;
import br.ufs.gothings.core.sink.SinkListener;
import br.ufs.gothings.core.sink.SinkLink;
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
    private final SinkLink<GwMessage> sinkLink;

    public MqttPluginClient(final SinkLink<GwMessage> sinkLink) {
        this.sinkLink = sinkLink;
        this.sinkLink.setListener(new MessageSinkListener());
        connections = new HashMap<>();
    }

    private class MessageSinkListener implements SinkListener<GwMessage> {
        @Override
        public void valueReceived(GwMessage msg) throws MqttException {
            final String host = msg.headers().targetsHeader().get(0);

            final MqttConnection conn = getMqttConnection(host);
            conn.sendMessage(msg);
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
                    final GwMessage msg = GwMessage.newAnswerMessage();
                    msg.payload().set(mqttMessage.getPayload());
                    final GwHeaders h = msg.headers();
                    h.targetsHeader().add(host);
                    h.pathHeader().set(topic);

                    sinkLink.send(msg);
                }

                // TODO: what to do here?
                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {

                }
            });
            connectionToken = client.connect(new MqttConnectOptions());
        }

        public void sendMessage(GwMessage msg) throws MqttException {
            connectionToken.waitForCompletion();
            final GwHeaders h = msg.headers();
            final Operation operation = h.operationHeader().get();
            final String topic = h.pathHeader().get();
            final int qos = max(0, min(2, h.qosHeader().get()));
            switch (operation) {
                // PUT and POST is mapped as a publish, but this plugin treats POST as a retained message
                case PUT:
                case POST:
                    final MqttMessage mqttMessage = new MqttMessage(msg.payload().asBytes());
                    mqttMessage.setRetained(operation == Operation.POST);
                    mqttMessage.setQos(qos);
                    client.publish(topic, mqttMessage);
                    break;

                // MQTT doesn't really specify a delete operation, but when a retained message with a zero byte payload
                // is sent, the broker removes the retained message
                case DELETE:
                    client.publish(topic, ArrayUtils.EMPTY_BYTE_ARRAY, 0, true);
                    break;

                // GET is unsurprisingly directly mapped to subscribe
                case GET:
                    client.subscribe(topic, qos);
                    break;
            }
        }
    }
}
