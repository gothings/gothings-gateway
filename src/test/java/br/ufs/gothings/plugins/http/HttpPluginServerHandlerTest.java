package br.ufs.gothings.plugins.http;

import br.ufs.gothings.core.CommunicationManager;
import br.ufs.gothings.core.GwHeaders;
import br.ufs.gothings.core.GwMessage;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.util.concurrent.ImmediateExecutor;
import org.junit.Test;

import java.util.concurrent.*;

import static io.netty.handler.codec.http.HttpMethod.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static org.junit.Assert.*;

/**
 * @author Wagner Macedo
 */
public class HttpPluginServerHandlerTest {

    @Test
    public void testGatewayPayloadIsUsed() {
        final ChannelHandler handler = new HttpPluginServerHandler(new DummyCM(new MessageCallable() {
            @Override
            public GwMessage call() {
                final GwHeaders h = message.headers();
                message.payload().clear().writeInt(h.getOperation().length() + h.getPath().length());
                return message;
            }
        }));
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        /*
        the response 'assert' statements check:
            - payload has the size of int (4 bytes), and
            - the int value is the length of strings METHOD+PATH, e.g. "GET/hello/world".length().
        */
        final DefaultFullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, GET, "/hello/world");
        request.retain();  // <- allow request object reusing
        assertFalse(channel.writeInbound(request));
        FullHttpResponse response = (FullHttpResponse) channel.readOutbound();
        assertEquals(4, response.content().readableBytes());
        assertEquals(15, response.content().readInt());

        request.setMethod(PUT);
        request.setUri("/hello");
        request.retain();
        assertFalse(channel.writeInbound(request));
        response = (FullHttpResponse) channel.readOutbound();
        assertEquals(4, response.content().readableBytes());
        assertEquals(9, response.content().readInt());

        request.setMethod(POST);
        request.retain();
        assertFalse(channel.writeInbound(request));
        response = (FullHttpResponse) channel.readOutbound();
        assertEquals(4, response.content().readableBytes());
        assertEquals(10, response.content().readInt());

        request.setMethod(DELETE);
        request.retain();
        assertFalse(channel.writeInbound(request));
        response = (FullHttpResponse) channel.readOutbound();
        assertEquals(4, response.content().readableBytes());
        assertEquals(12, response.content().readInt());

        assertFalse(channel.finish());
        assertNull(channel.readOutbound());
    }
}

/* A dummy CommunicationManager to allow simulate changes on a gateway message */
class DummyCM implements CommunicationManager {
    private CompletionService<GwMessage> service = new ExecutorCompletionService<>(ImmediateExecutor.INSTANCE);
    private MessageCallable callable;

    DummyCM(MessageCallable callable) {
        this.callable = callable;
    }

    @Override
    public Future<GwMessage> sendRequest(GwMessage message) {
        callable.message = message;
        return service.submit(callable);
    }
}

/* To supply code to DummyCM for making custom changes to the gateway message */
abstract class MessageCallable implements Callable<GwMessage> {
    GwMessage message;

    public abstract GwMessage call();
}
