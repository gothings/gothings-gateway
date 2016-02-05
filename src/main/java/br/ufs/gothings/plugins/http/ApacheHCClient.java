package br.ufs.gothings.plugins.http;

import br.ufs.gothings.core.common.ErrorCode;
import br.ufs.gothings.core.message.GwError;
import br.ufs.gothings.core.message.GwReply;
import br.ufs.gothings.core.message.GwRequest;
import br.ufs.gothings.core.message.headers.GwHeaders;
import br.ufs.gothings.core.message.headers.Operation;
import br.ufs.gothings.core.plugin.ReplyLink;
import br.ufs.gothings.core.util.Polling;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.*;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

import static br.ufs.gothings.core.message.headers.HeaderNames.*;

/**
 * @author Wagner Macedo
 */
public class ApacheHCClient {
    private ReplyLink replyLink;
    private CloseableHttpClient httpClient;

    private Polling polling;

    public void start(final ReplyLink replyLink) {
        this.replyLink = replyLink;
        this.httpClient = HttpClients.createDefault();
        this.polling = new Polling(
                dst -> sendGET(dst.getRequest()),
                5, TimeUnit.MINUTES);
        this.polling.start();
    }

    public void stop() {
        this.replyLink = null;
        this.polling.stop();
        this.polling = null;
        try {
            this.httpClient.close();
        } catch (IOException ignored) {
        }
        this.httpClient = null;
    }

    public void sendRequest(final GwRequest request) {
        final GwHeaders h = request.headers();
        final Operation operation = h.get(GW_OPERATION);

        // READ, OBSERVE and UNOBSERVE are processed a bit different
        switch (operation) {
            case READ:
                sendGET(request);
                return;
            case OBSERVE:
                polling.add(request);
                return;
            case UNOBSERVE:
                polling.del(request);
                return;
        }

        final String uri = createURI(h);

        // Choose HTTP method by the gateway operation
        final HttpUriRequest httpRequest;
        switch (operation) {
            case CREATE:
                httpRequest = new HttpPost(uri);
                break;
            case UPDATE:
                httpRequest = new HttpPut(uri);
                break;
            case DELETE:
                httpRequest = new HttpDelete(uri);
                break;
            default:
                return;
        }

        switch (operation) {
            case CREATE:
            case UPDATE:
                httpRequest.addHeader("Content-Type", h.get(GW_CONTENT_TYPE));
                break;
        }

        final CloseableHttpResponse httpResponse = executeHttpRequest(httpRequest, request);
        if (httpResponse == null)
            return;

        // Payload handling
        try (CloseableHttpResponse r = httpResponse) {
            // Ensure response body is consumed for safely reusing connection
            EntityUtils.consume(r.getEntity());
        } catch (IOException e) {
            replyLink.sendError(new GwError(request, ErrorCode.OTHER));
            return;
        }

        // Send reply ack for CREATE, UPDATE and DELETE operations
        replyLink.ack(request.getSequence());
    }

    private void sendGET(final GwRequest request) {
        final GwHeaders h = request.headers();
        final HttpGet httpRequest = new HttpGet(createURI(h));

        final String value = String.join(",", h.getAll(GW_EXPECTED_TYPES)) + ",*/*;q=0.5";
        httpRequest.addHeader("Accept", value);

        final CloseableHttpResponse httpResponse = executeHttpRequest(httpRequest, request);
        if (httpResponse == null)
            return;

        // Payload handling
        try (CloseableHttpResponse r = httpResponse) {
            final HttpEntity entity = r.getEntity();
            final GwReply reply = new GwReply(request);
            reply.payload().set(EntityUtils.toByteArray(entity));
            replyLink.send(reply);
        } catch (IOException e) {
            replyLink.sendError(new GwError(request, ErrorCode.OTHER));
        }
    }

    private CloseableHttpResponse executeHttpRequest(final HttpUriRequest httpRequest, final GwRequest request) {
        final CloseableHttpResponse httpResponse;
        try {
            httpResponse = httpClient.execute(httpRequest);
        } catch (IOException e) {
            if (e instanceof SocketException || e instanceof UnknownHostException) {
                replyLink.sendError(new GwError(request, ErrorCode.TARGET_NOT_FOUND));
            } else {
                replyLink.sendError(new GwError(request, ErrorCode.OTHER));
            }
            return null;
        }

        // Error handling
        final int statusCode = httpResponse.getStatusLine().getStatusCode();
        if (statusCode >= 400) {
            if (statusCode == 404) {
                replyLink.sendError(new GwError(request, ErrorCode.PATH_NOT_FOUND));
            } else {
                replyLink.sendError(new GwError(request, ErrorCode.OTHER));
            }
            return null;
        }

        return httpResponse;
    }

    private static String createURI(final GwHeaders headers) {
        return "http://" + headers.get(GW_TARGET) + headers.get(GW_PATH);
    }
}
