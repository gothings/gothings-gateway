package br.ufs.gothings.plugins.mqtt;

import br.ufs.gothings.core.GwMessage;
import br.ufs.gothings.core.message.Operation;
import br.ufs.gothings.core.sink.Sink;
import br.ufs.gothings.core.sink.SinkLink;
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
        final Sink<GwMessage> sink = new Sink<>();
        new MqttPluginClient(sink.getRightLink());

        final SynchronousQueue<GwMessage> pipe = new SynchronousQueue<>();
        final SinkLink<GwMessage> link = sink.getLeftLink();
        link.setListener(pipe::put);

        GwMessage msg = GwMessage.newMessage();
        msg.headers().targetsHeader().add("localhost");
        msg.headers().operationHeader().set(Operation.GET);
        msg.headers().pathHeader().set("temperature");
        link.send(msg);

        msg = pipe.take();
        assertTrue(msg.isAnswer());
        assertEquals("localhost", msg.headers().targetsHeader().get(0));
        assertEquals("temperature", msg.headers().pathHeader().get());
        assertEquals("88 C", msg.payload().asString(Charset.defaultCharset()));
    }
}
