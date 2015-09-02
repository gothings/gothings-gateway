package br.ufs.gothings.plugins.http;

import br.ufs.gothings.core.GwHeaders;
import br.ufs.gothings.core.GwMessage;
import br.ufs.gothings.core.message.Operation;
import br.ufs.gothings.core.sink.Sink;
import br.ufs.gothings.core.sink.SinkLink;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import org.junit.Test;

import java.nio.charset.Charset;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static org.junit.Assert.*;

/**
 * @author Wagner Macedo
 */
public class NettyServerHandlerTest {
    @Test
    public void testGatewayPayloadIsUsed() {
        final Sink<GwMessage> sink = new Sink<>();
        final SinkLink<GwMessage> link = sink.getLeftLink();
        link.setListener(message -> {
            final GwHeaders h = message.headers();

            final Operation operation = h.operationHeader().get();
            final String path = h.pathHeader().get();
            final ByteBuf buf = Unpooled.buffer().writeInt(operation.name().length() + path.length());

            final GwMessage answer = GwMessage.newAnswerMessage(message);
            answer.payload().set(buf.nioBuffer());
            link.send(answer);
        });

        final ChannelHandler handler = new NettyServerHandler(sink.getRightLink());
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        /*
        the response 'assert' statements check:
            - payload has the size of int (4 bytes), and
            - the int value is the length of strings OPERATION+PATH, e.g. "READ/hello/world".length().
        */
        final DefaultFullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, HttpMethod.GET, "/hello/world");
        request.retain();  // <- allow request object reusing
        assertFalse(channel.writeInbound(request));
        FullHttpResponse response = (FullHttpResponse) channel.readOutbound();
        assertEquals(4, response.content().readableBytes());
        assertEquals(16, response.content().readInt()); // "READ/hello/world"

        request.setMethod(HttpMethod.PUT);
        request.setUri("/hello");
        request.retain();
        assertFalse(channel.writeInbound(request));
        response = (FullHttpResponse) channel.readOutbound();
        assertEquals(4, response.content().readableBytes());
        assertEquals(12, response.content().readInt()); // "UPDATE/hello"

        request.setMethod(HttpMethod.POST);
        request.retain();
        assertFalse(channel.writeInbound(request));
        response = (FullHttpResponse) channel.readOutbound();
        assertEquals(4, response.content().readableBytes());
        assertEquals(12, response.content().readInt()); // "CREATE/hello"

        request.setMethod(HttpMethod.DELETE);
        request.retain();
        assertFalse(channel.writeInbound(request));
        response = (FullHttpResponse) channel.readOutbound();
        assertEquals(4, response.content().readableBytes());
        assertEquals(12, response.content().readInt()); // "DELETE/hello"

        assertFalse(channel.finish());
        assertNull(channel.readOutbound());
    }

    @Test
    public void testGatewayHeadersAreUsed() throws InterruptedException {
        final Sink<GwMessage> sink = new Sink<>();
        final SinkLink<GwMessage> link = sink.getLeftLink();
        link.setListener(message -> {
            final GwMessage answer = GwMessage.newAnswerMessage(message);
            answer.payload().set("{\"array\":[1,2,3]}", Charset.defaultCharset());
            final GwHeaders h = answer.headers();
            h.contentTypeHeader().set("application/json");

            link.send(answer);
        });

        final ChannelHandler handler = new NettyServerHandler(sink.getRightLink());
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        final DefaultFullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, HttpMethod.GET, "/path");
        assertFalse(channel.writeInbound(request));
        FullHttpResponse response = (FullHttpResponse) channel.readOutbound();
        assertEquals("application/json", response.headers().get(CONTENT_TYPE));
    }
}
