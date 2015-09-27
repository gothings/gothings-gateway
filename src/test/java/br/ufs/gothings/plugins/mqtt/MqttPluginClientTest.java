package br.ufs.gothings.plugins.mqtt;

import br.ufs.gothings.core.message.GwReply;
import br.ufs.gothings.core.message.GwRequest;
import br.ufs.gothings.core.plugin.error.ReplyError;
import br.ufs.gothings.core.message.headers.Operation;
import br.ufs.gothings.core.plugin.ReplyLink;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.junit.Test;

import java.nio.charset.Charset;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.SynchronousQueue;

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
        req.headers().setTarget("localhost");
        req.headers().setOperation(Operation.READ);
        req.headers().setPath("temperature");
        pluginClient.sendRequest(req);

        final GwReply reply = replyLink.receive();
        assertEquals("localhost", reply.headers().getTarget());
        assertEquals("temperature", reply.headers().getPath());
        assertEquals("88 C", reply.payload().asString(Charset.defaultCharset()));
    }

    private static class SynchronousReplyLink implements ReplyLink {
        private final SynchronousQueue<GwReply> pipe = new SynchronousQueue<>();

        @Override
        public void send(final GwReply reply) {
            try {
                pipe.put(reply);
            } catch (InterruptedException ignored) {
            }
        }

        @Override
        public void error(final ReplyError e) {
            // needless for tests
        }

        public GwReply receive() throws InterruptedException {
            return pipe.take();
        }
    }
}
