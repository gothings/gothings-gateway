package br.ufs.gothings.plugins.http;

import br.ufs.gothings.core.message.GwHeaders;
import br.ufs.gothings.core.message.GwReply;
import br.ufs.gothings.core.message.Payload;
import br.ufs.gothings.core.message.headers.Operation;
import br.ufs.gothings.core.plugin.RequestLink;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.BasicHttpResponse;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import static org.junit.Assert.*;

/**
 * @author Wagner Macedo
 */
public class ApacheHCServerTest {
    @Test
    public void testGatewayPayloadIsUsed() throws HttpException, IOException {
        final RequestLink requestLink = msg -> {
            final GwHeaders h = msg.headers();

            final Operation operation = h.getOperation();
            final String path = h.getPath();
            final ByteBuf buf = Unpooled.buffer().writeInt(operation.name().length() + path.length());

            final GwReply reply = new GwReply(msg.headers(), msg.payload(), 1L);
            reply.payload().set(buf.nioBuffer());

            return Utils.constantReply(reply);
        };

        ApacheHCServer.ServerRequestHandler serverHandler = new ApacheHCServer.ServerRequestHandler(requestLink);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, null);

        /*
        the response 'assert' statements check:
            - payload has the size of int (4 bytes), and
            - the int value is the length of strings OPERATION+PATH, e.g. "READ/hello/world".length().
        */
        serverHandler.handle(newRequest("GET", "/hello/world"), response, null);
        {
            final Payload payload = new Payload();
            payload.set(response.getEntity().getContent(), false);
            final ByteBuffer buffer = payload.asBuffer();
            assertEquals(4, buffer.remaining());
            assertEquals(16, buffer.getInt()); // "READ/hello/world"
        }

        serverHandler.handle(newRequest("PUT", "/hello"), response, null);
        {
            final Payload payload = new Payload();
            payload.set(response.getEntity().getContent(), false);
            final ByteBuffer buffer = payload.asBuffer();
            assertEquals(4, buffer.remaining());
            assertEquals(12, buffer.getInt()); // "UPDATE/hello"
        }

        serverHandler.handle(newRequest("POST", "/hello"), response, null);
        {
            final Payload payload = new Payload();
            payload.set(response.getEntity().getContent(), false);
            final ByteBuffer buffer = payload.asBuffer();
            assertEquals(4, buffer.remaining());
            assertEquals(12, buffer.getInt()); // "CREATE/hello"
        }

        serverHandler.handle(newRequest("DELETE", "/hello"), response, null);
        {
            final Payload payload = new Payload();
            payload.set(response.getEntity().getContent(), false);
            final ByteBuffer buffer = payload.asBuffer();
            assertEquals(4, buffer.remaining());
            assertEquals(12, buffer.getInt()); // "DELETE/hello"
        }
    }

    @Test
    public void testGatewayHeadersAreUsed() throws HttpException {
        final RequestLink requestLink = msg -> {
            final GwReply reply = new GwReply(msg.headers(), msg.payload(), 1L);
            reply.payload().set("{\"array\":[1,2,3]}", Charset.defaultCharset());
            reply.headers().setContentType("application/json");

            return Utils.constantReply(reply);
        };

        final ApacheHCServer.ServerRequestHandler serverHandler = new ApacheHCServer.ServerRequestHandler(requestLink);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, null);

        serverHandler.handle(newRequest("GET", "/path"), response, null);
        assertEquals("application/json", response.getFirstHeader("Content-Type").getValue());
    }

    private static HttpRequest newRequest(final String method, final String uri) {
        return new BasicHttpRequest(method, uri, HttpVersion.HTTP_1_1);
    }
}
