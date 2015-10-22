package br.ufs.gothings.plugins.mqtt;

import br.ufs.gothings.core.message.GwError;
import br.ufs.gothings.core.message.GwReply;
import br.ufs.gothings.core.message.GwRequest;
import br.ufs.gothings.core.message.headers.Operation;
import br.ufs.gothings.core.plugin.ReplyLink;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.junit.Test;

import java.nio.charset.Charset;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.SynchronousQueue;

import static br.ufs.gothings.core.message.headers.HeaderNames.GW_OPERATION;
import static br.ufs.gothings.core.message.headers.HeaderNames.GW_PATH;
import static br.ufs.gothings.core.message.headers.HeaderNames.GW_TARGET;
import static org.junit.Assert.*;

/*
The tests in this class need an already started localhost broker.
This will be fixed at any moment later by embed a MQTT broker.
*/

/**
 * @author Wagner Macedo
 */
public class MqttPluginClientTest {
    @Test
    public void testSubscribe() throws MqttException, ExecutionException, InterruptedException {
        final SynchronousReplyLink replyLink = new SynchronousReplyLink();
        final MqttPluginClient pluginClient = new MqttPluginClient(replyLink);

        final GwRequest req = new GwRequest();
        req.headers().set(GW_TARGET, "localhost");
        req.headers().set(GW_OPERATION, Operation.READ);
        req.headers().set(GW_PATH, "temperature");
        pluginClient.sendRequest(req);

        final GwReply reply = replyLink.receive();
        assertEquals("localhost", reply.headers().get(GW_TARGET));
        assertEquals("temperature", reply.headers().get(GW_PATH));
        assertEquals("88 C", reply.payload().asString(Charset.defaultCharset()));
    }

    private static class SynchronousReplyLink implements ReplyLink {
        private final SynchronousQueue<GwReply> pipe = new SynchronousQueue<>();

        @Override
        public void ack(final long sequence) {
            // needless for tests
        }

        @Override
        public void send(final GwReply reply) {
            try {
                pipe.put(reply);
            } catch (InterruptedException ignored) {
            }
        }

        @Override
        public void sendError(final GwError error) {
            // needless for tests
        }

        public GwReply receive() throws InterruptedException {
            return pipe.take();
        }
    }
}
