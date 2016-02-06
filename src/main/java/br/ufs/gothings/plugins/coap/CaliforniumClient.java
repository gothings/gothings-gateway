package br.ufs.gothings.plugins.coap;

import br.ufs.gothings.core.common.ErrorCode;
import br.ufs.gothings.core.message.GwError;
import br.ufs.gothings.core.message.GwReply;
import br.ufs.gothings.core.message.GwRequest;
import br.ufs.gothings.core.message.headers.GwHeaders;
import br.ufs.gothings.core.message.headers.Operation;
import br.ufs.gothings.core.plugin.ReplyLink;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.coap.CoAP;
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

        // READ is processed a bit different
        switch (operation) {
            case READ:
                sendGET(request);
                return;
        }

        // Choose CoAP request code by the gateway operation
        final Request coapRequest;
        switch (operation) {
            case CREATE:
                coapRequest = Request.newPost();
                break;
            case UPDATE:
                coapRequest = Request.newPut();
                break;
            case DELETE:
                coapRequest = Request.newDelete();
                break;
            default:
                return;
        }

        // Set CREATE and UPDATE options
        final OptionSet coapOptions = coapRequest.getOptions();
        switch (operation) {
            case CREATE:
            case UPDATE:
                final int format = MediaTypeRegistry.parse(h.get(GW_CONTENT_TYPE));
                if (format != MediaTypeRegistry.UNDEFINED)
                    coapOptions.setContentFormat(format);
        }
        setCoapQoS(coapRequest, h);

        // Get CoAP response
        final CoapResponse coapResponse = getCoapResponse(coapRequest, request);
        if (coapResponse == null)
            return;

        // Send reply ack: no payload for CREATE, UPDATE and DELETE operations
        replyLink.ack(request.getSequence());
    }

    private void sendGET(final GwRequest request) {
        final GwHeaders h = request.headers();
        final Request coapRequest = Request.newGet();

        // Set GET options
        final OptionSet coapOptions = coapRequest.getOptions();
        final int format = MediaTypeRegistry.parse(h.get(GW_EXPECTED_TYPES));
        if (format != MediaTypeRegistry.UNDEFINED) {
            coapOptions.setAccept(format);
        }
        setCoapQoS(coapRequest, h);

        // Get CoAP response
        final CoapResponse coapResponse = getCoapResponse(coapRequest, request);
        if (coapResponse == null) {
            return;
        }

        // Payload handling
        final GwReply reply = new GwReply(request);
        reply.payload().set(coapResponse.getPayload());
        replyLink.send(reply);
    }

    private static void setCoapQoS(final Request coapRequest, final GwHeaders h) {
        final int qos = h.get(GW_QOS, 1);
        coapRequest.setType(qos == 0 ? CoAP.Type.NON : CoAP.Type.CON);
    }

    private CoapResponse getCoapResponse(final Request coapRequest, final GwRequest request) {
        final CoapClient coapClient = new CoapClient(createURI(request.headers()));

        final CoapResponse coapResponse = coapClient.advanced(coapRequest);
        if (coapResponse == null) {
            replyLink.sendError(new GwError(request, ErrorCode.OTHER));
            return null;
        }

        // Error handling
        final ResponseCode code = coapResponse.getCode();
        if (!ResponseCode.isSuccess(code)) {
            switch (code) {
                case NOT_FOUND:
                    replyLink.sendError(new GwError(request, ErrorCode.PATH_NOT_FOUND));
                    return null;
                default:
                    replyLink.sendError(new GwError(request, ErrorCode.OTHER));
                    return null;
            }
        }
        return coapResponse;
    }

    private String createURI(final GwHeaders h) {
        return "coap://" + h.get(GW_TARGET) + h.get(GW_PATH);
    }
}
