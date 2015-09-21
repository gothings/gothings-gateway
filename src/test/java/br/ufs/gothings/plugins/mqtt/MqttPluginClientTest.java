package br.ufs.gothings.plugins.mqtt;

import br.ufs.gothings.core.GwMessage;
import br.ufs.gothings.core.message.GwReply;
import br.ufs.gothings.core.message.GwRequest;
import br.ufs.gothings.core.message.headers.Operation;
import br.ufs.gothings.core.message.sink.MessageLink;
import br.ufs.gothings.core.message.sink.MessageSink;
import org.junit.Test;

import java.nio.charset.Charset;
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
    public void testSubscribe() throws InterruptedException {
        final MessageSink sink = new MessageSink();
        new MqttPluginClient(sink.getRightLink());

        final SynchronousQueue<GwMessage> pipe = new SynchronousQueue<>();
        final MessageLink link = sink.getLeftLink();
        link.setUp(pipe::put);

        final GwRequest req = new GwRequest();
        req.headers().setTarget("localhost");
        req.headers().setOperation(Operation.READ);
        req.headers().setPath("temperature");
        link.sendRequest(req);

        final GwMessage message = pipe.take();
        assertTrue("message is not instance of GwReply", message instanceof GwReply);

        final GwReply reply = (GwReply) message;
        assertEquals("localhost", reply.headers().getTarget());
        assertEquals("temperature", reply.headers().getPath());
        assertEquals("88 C", reply.payload().asString(Charset.defaultCharset()));
    }
}
