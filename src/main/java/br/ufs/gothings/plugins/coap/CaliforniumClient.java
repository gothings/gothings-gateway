package br.ufs.gothings.plugins.coap;

import br.ufs.gothings.core.common.Reason;
import br.ufs.gothings.core.message.GwError;
import br.ufs.gothings.core.message.GwReply;
import br.ufs.gothings.core.message.GwRequest;
import br.ufs.gothings.core.message.headers.GwHeaders;
import br.ufs.gothings.core.message.headers.Operation;
import br.ufs.gothings.core.plugin.ReplyLink;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.OptionSet;
import org.eclipse.californium.core.coap.Request;

import static br.ufs.gothings.core.message.headers.HeaderNames.*;

/**
 * @author Wagner Macedo
 */
public class CaliforniumClient {
    private ReplyLink replyLink;

    public void start(final ReplyLink replyLink) {
        this.replyLink = replyLink;
    }

    public void stop() {
        this.replyLink = null;
    }

    public void sendRequest(final GwRequest request) {
        final GwHeaders h = request.headers();
        final Operation operation = h.get(GW_OPERATION);
        final String uri = "coap://" + h.get(GW_TARGET) + h.get(GW_PATH);

        final CoapClient coapClient = new CoapClient(uri);

        // Choose CoAP request code by the gateway operation
        final Request coapRequest;
        switch (operation) {
            case CREATE:
                coapRequest = Request.newPost();
                break;
            case READ:
                coapRequest = Request.newGet();
                break;
            case UPDATE:
                coapRequest = Request.newPut();
                break;
            case DELETE:
                coapRequest = Request.newDelete();
                break;
            default:
                coapRequest = null;
        }

        if (coapRequest == null) {
            // do something for observe/unobserve operation and stop
            return;
        }

        final OptionSet coapOptions = coapRequest.getOptions();
        switch (operation) {
            case CREATE:
            case UPDATE:
                coapOptions.setContentFormat(MediaTypeRegistry.parse(h.get(GW_CONTENT_TYPE)));
                break;
            case READ:
                coapOptions.setAccept(MediaTypeRegistry.parse(h.get(GW_EXPECTED_TYPES)));
                break;
        }

        final CoapResponse coapResponse = coapClient.advanced(coapRequest);
        if (coapResponse == null) {
            replyLink.sendError(new GwError(request, Reason.OTHER));
            return;
        }

        // Error handling
        final ResponseCode code = coapResponse.getCode();
        if (!ResponseCode.isSuccess(code)) {
            switch (code) {
                case NOT_FOUND:
                    replyLink.sendError(new GwError(request, Reason.PATH_NOT_FOUND));
                    return;
                default:
                    replyLink.sendError(new GwError(request, Reason.OTHER));
                    return;
            }
        }

        // Payload handling
        if (operation == Operation.READ) {
            final GwReply reply = new GwReply(request);
            reply.payload().set(coapResponse.getPayload());
            replyLink.send(reply);
            return;
        }

        // Send reply ack for CREATE, UPDATE and DELETE operations
        replyLink.ack(request.getSequence());
    }
}
