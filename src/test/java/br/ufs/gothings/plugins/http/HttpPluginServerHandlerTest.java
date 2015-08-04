package br.ufs.gothings.plugins.http;

import br.ufs.gothings.core.GwHeaders;
import br.ufs.gothings.core.GwMessage;
import br.ufs.gothings.core.sink.Sink;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpMethod.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static org.junit.Assert.*;

/**
 * @author Wagner Macedo
 */
public class HttpPluginServerHandlerTest {
    private static ExecutorService EXECUTOR;

    @Before
    public void startExecutor() {
        EXECUTOR = Executors.newSingleThreadExecutor();
    }

    @After
    public void stopExecutor() {
        EXECUTOR.shutdown();
    }

    @Test
    public void testGatewayPayloadIsUsed() {
        final Sink<GwMessage> sink = new Sink<>(EXECUTOR);
        sink.setListener(evt -> {
            final GwMessage message = evt.getValue();
            final GwHeaders h = message.headers();
            message.payload().clear().writeInt(h.operation().getValue().name().length() + h.path().getValue().length());
        });
        final ChannelHandler handler = new HttpPluginServerHandler(sink);
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

    @Test
    public void testGatewayHeadersAreUsed() throws InterruptedException {
        final Sink<GwMessage> sink = new Sink<>(EXECUTOR);
        sink.setListener(evt -> {
            final GwMessage message = evt.getValue();
            message.setPayload("{\"array\":[1,2,3]}");
            final GwHeaders h = message.headers();
            h.contentType().setValue("application/json");
        });
        final ChannelHandler handler = new HttpPluginServerHandler(sink);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        final DefaultFullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, GET, "/path");
        assertFalse(channel.writeInbound(request));
        FullHttpResponse response = (FullHttpResponse) channel.readOutbound();
        assertEquals("application/json", response.headers().get(CONTENT_TYPE));
    }
}
