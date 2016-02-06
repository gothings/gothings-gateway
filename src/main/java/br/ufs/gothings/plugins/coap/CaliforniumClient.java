package br.ufs.gothings.plugins.coap;

import br.ufs.gothings.core.common.ErrorCode;
import br.ufs.gothings.core.message.GwError;
import br.ufs.gothings.core.message.GwReply;
import br.ufs.gothings.core.message.GwRequest;
import br.ufs.gothings.core.message.headers.GwHeaders;
import br.ufs.gothings.core.message.headers.Operation;
import br.ufs.gothings.core.plugin.ReplyLink;
import br.ufs.gothings.core.util.Polling;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.coap.*;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import static br.ufs.gothings.core.message.headers.HeaderNames.*;

/**
 * @author Wagner Macedo
 */
public class CaliforniumClient {
    private ReplyLink replyLink;

    private ConcurrentMap<String, Request> observers;
    private Polling polling;

    public void start(final ReplyLink replyLink) {
        this.replyLink = replyLink;
        this.observers = new ConcurrentHashMap<>();
        this.polling = new Polling(
                dst -> sendGET(dst.getRequest()),
                5, TimeUnit.MINUTES);
        this.polling.start();
    }

    public void stop() {
        this.replyLink = null;
        this.polling.stop();
        this.polling = null;
    }

    public void sendRequest(final GwRequest request) {
        final GwHeaders h = request.headers();
        final Operation operation = h.get(GW_OPERATION);

        // READ, OBSERVE and UNOBSERVE are processed a bit different
        switch (operation) {
            case READ:
            case OBSERVE:
                sendGET(request);
                return;
            case UNOBSERVE:
                final String uri = createURI(h);
                observers.compute(uri,
                        (k, req) -> {
                            if (req == null) {
                                polling.del(request);
                            } else {
                                req.cancel();
                            }
                            return null;
                        });
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

        // Set Observe flag if applicable
        if (h.get(GW_OPERATION) == Operation.OBSERVE) {
            coapRequest.setObserve();
        }

        // Get CoAP response asynchronously
        coapRequest.addMessageObserver(new MessageObserverAdapter() {
            @Override
            public void onResponse(final Response response) {
                final GwReply reply = new GwReply(request);
                reply.payload().set(response.getPayload());
                replyLink.send(reply);
            }
        });

        final CoapResponse coapResponse = getCoapResponse(coapRequest, request);
        if (coapResponse == null) {
            return;
        }

        if (coapResponse.getOptions().hasObserve()) {
            final String uri = createURI(h);
            observers.put(uri, coapRequest);
        } else {
            coapRequest.cancel();
            polling.add(request, false);
        }
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
