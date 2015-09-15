package br.ufs.gothings.plugins.mqtt;

import br.ufs.gothings.core.message.DataMessage;
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

        final SynchronousQueue<DataMessage> pipe = new SynchronousQueue<>();
        final MessageLink link = sink.getLeftLink();
        link.setUp(pipe::put);

        final GwRequest req = new GwRequest();
        req.headers().targetHeader().set("localhost");
        req.headers().operationHeader().set(Operation.READ);
        req.headers().pathHeader().set("temperature");
        link.sendRequest(req);

        final DataMessage reply = pipe.take();
        assertTrue("reply is not instance of GwNews", reply instanceof GwReply);
        assertEquals("localhost", reply.headers().targetHeader().get());
        assertEquals("temperature", reply.headers().pathHeader().get());
        assertEquals("88 C", reply.payload().asString(Charset.defaultCharset()));
    }
}
