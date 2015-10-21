package br.ufs.gothings.plugins.http;

import br.ufs.gothings.core.message.GwHeaders;
import br.ufs.gothings.core.message.GwReply;
import br.ufs.gothings.core.message.Payload;
import br.ufs.gothings.core.message.headers.Operation;
import br.ufs.gothings.core.plugin.RequestLink;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.http.client.methods.*;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import static org.junit.Assert.*;

/**
 * @author Wagner Macedo
 */
@Deprecated
public class NanoHTTPDServerTest {
    @Test
    public void testGatewayPayloadIsUsed() throws IOException, URISyntaxException {
        final RequestLink requestLink = msg -> {
            final GwHeaders h = msg.headers();

            final Operation operation = h.getOperation();
            final String path = h.getPath();
            final ByteBuf buf = Unpooled.buffer().writeInt(operation.name().length() + path.length());

            final GwReply reply = new GwReply(msg.headers(), msg.payload(), 1L);
            reply.payload().set(buf.nioBuffer());

            return Utils.constantReply(reply);
        };

        final NanoHTTPDServer.Server server = new NanoHTTPDServer.Server(requestLink, 0);
        server.start();

        /*
        the response 'assert' statements check:
            - payload has the size of int (4 bytes), and
            - the int value is the length of strings OPERATION+PATH, e.g. "READ/hello/world".length().
        */
        final CloseableHttpClient httpclient = HttpClients.createDefault();
        final URIBuilder uri = new URIBuilder("http://localhost:" + server.getListeningPort());

        uri.setPath("/hello/world");
        try (CloseableHttpResponse response = httpclient.execute(new HttpGet(uri.build()))) {
            final Payload payload = new Payload();
            payload.set(response.getEntity().getContent(), false);
            final ByteBuffer buffer = payload.asBuffer();
            assertEquals(4, buffer.remaining());
            assertEquals(16, buffer.getInt()); // "READ/hello/world"
        }

        uri.setPath("/hello");
        try (CloseableHttpResponse response = httpclient.execute(new HttpPut(uri.build()))) {
            final Payload payload = new Payload();
            payload.set(response.getEntity().getContent(), false);
            final ByteBuffer buffer = payload.asBuffer();
            assertEquals(4, buffer.remaining());
            assertEquals(12, buffer.getInt()); // "UPDATE/hello"
        }

        try (CloseableHttpResponse response = httpclient.execute(new HttpPost(uri.build()))) {
            final Payload payload = new Payload();
            payload.set(response.getEntity().getContent(), false);
            final ByteBuffer buffer = payload.asBuffer();
            assertEquals(4, buffer.remaining());
            assertEquals(12, buffer.getInt()); // "CREATE/hello"
        }

        try (CloseableHttpResponse response = httpclient.execute(new HttpDelete(uri.build()))) {
            final Payload payload = new Payload();
            payload.set(response.getEntity().getContent(), false);
            final ByteBuffer buffer = payload.asBuffer();
            assertEquals(4, buffer.remaining());
            assertEquals(12, buffer.getInt()); // "DELETE/hello"
        }

        server.stop();
    }

    @Test
    public void testGatewayHeadersAreUsed() throws IOException, URISyntaxException {
        final RequestLink requestLink = msg -> {
            final GwReply reply = new GwReply(msg.headers(), msg.payload(), 1L);
            reply.payload().set("{\"array\":[1,2,3]}", Charset.defaultCharset());
            reply.headers().setContentType("application/json");

            return Utils.constantReply(reply);
        };

        final NanoHTTPDServer.Server server = new NanoHTTPDServer.Server(requestLink, 0);
        server.start();

        final CloseableHttpClient httpclient = HttpClients.createDefault();
        final URIBuilder uri = new URIBuilder("http://localhost:" + server.getListeningPort() + "/path");

        try (CloseableHttpResponse response = httpclient.execute(new HttpGet(uri.build()))) {
            assertEquals("application/json", response.getFirstHeader("Content-Type").getValue());
        }

        server.stop();
    }
}
