package br.ufs.gothings.plugins.http;

import br.ufs.gothings.core.common.Reason;
import br.ufs.gothings.core.message.GwError;
import br.ufs.gothings.core.message.GwReply;
import br.ufs.gothings.core.message.GwRequest;
import br.ufs.gothings.core.message.headers.GwHeaders;
import br.ufs.gothings.core.message.headers.Operation;
import br.ufs.gothings.core.plugin.ReplyLink;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.*;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.SocketException;
import java.net.UnknownHostException;

import static br.ufs.gothings.core.message.headers.HeaderNames.*;

/**
 * @author Wagner Macedo
 */
public class ApacheHCClient {
    private ReplyLink replyLink;
    private CloseableHttpClient httpClient;

    public void start(final ReplyLink replyLink) {
        this.replyLink = replyLink;
        this.httpClient = HttpClients.createDefault();
    }

    public void stop() {
        this.replyLink = null;
        try {
            this.httpClient.close();
        } catch (IOException ignored) {
        }
        this.httpClient = null;
    }

    public void sendRequest(final GwRequest request) {
        final GwHeaders h = request.headers();
        final Operation operation = h.get(GW_OPERATION);
        final String uri = "http://" + h.get(GW_TARGET) + h.get(GW_PATH);

        // Choose HTTP method by the gateway operation
        final HttpUriRequest httpRequest;
        switch (operation) {
            case CREATE:
                httpRequest = new HttpPost(uri);
                break;
            case READ:
                httpRequest = new HttpGet(uri);
                break;
            case UPDATE:
                httpRequest = new HttpPut(uri);
                break;
            case DELETE:
                httpRequest = new HttpDelete(uri);
                break;
            case OBSERVE:
            case UNOBSERVE:
            default:
                httpRequest = null;
        }

        if (httpRequest == null) {
            // do something for observe/unobserve operation and stop
            return;
        }

        switch (operation) {
            case CREATE:
            case UPDATE:
                httpRequest.addHeader("Content-Type", h.get(GW_CONTENT_TYPE));
                break;
            case READ:
                final String value = String.join(",", h.getAll(GW_EXPECTED_TYPES)) + ",*/*;q=0.5";
                httpRequest.addHeader("Accept", value);
                break;
        }

        final CloseableHttpResponse httpResponse;
        try {
            httpResponse = httpClient.execute(httpRequest);
        } catch (IOException e) {
            if (e instanceof SocketException || e instanceof UnknownHostException) {
                replyLink.sendError(new GwError(request, Reason.TARGET_NOT_FOUND));
            } else {
                replyLink.sendError(new GwError(request, Reason.OTHER));
            }
            return;
        }

        final int statusCode = httpResponse.getStatusLine().getStatusCode();

        // Error handling
        if (statusCode >= 400) {
            if (statusCode == 404) {
                replyLink.sendError(new GwError(request, Reason.PATH_NOT_FOUND));
            } else {
                replyLink.sendError(new GwError(request, Reason.OTHER));
            }
            return;
        }

        // Payload handling
        try (CloseableHttpResponse r = httpResponse) {
            final HttpEntity entity = r.getEntity();
            // Send reply for READ operation
            if (operation == Operation.READ) {
                final GwReply reply = new GwReply(request);
                reply.payload().set(EntityUtils.toByteArray(entity));
                replyLink.send(reply);
                return;
            }
            // Ensure response body is consumed for safely reusing connection
            else {
                EntityUtils.consume(entity);
            }
        } catch (IOException e) {
            replyLink.sendError(new GwError(request, Reason.OTHER));
            return;
        }

        // Send reply ack for CREATE, UPDATE and DELETE operations
        replyLink.ack(request.getSequence());
    }
}
