package br.ufs.gothings.plugins.coap;

import br.ufs.gothings.core.Settings;
import br.ufs.gothings.core.common.GatewayException;
import br.ufs.gothings.core.message.GwReply;
import br.ufs.gothings.core.message.GwRequest;
import br.ufs.gothings.core.message.headers.GwHeaders;
import br.ufs.gothings.core.message.headers.Operation;
import br.ufs.gothings.core.plugin.FutureReply;
import br.ufs.gothings.core.plugin.RequestLink;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.OptionSet;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.network.Exchange;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.server.resources.Resource;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static br.ufs.gothings.core.message.headers.HeaderNames.*;

/**
 * @author Wagner Macedo
 */
public class CaliforniumServer {

    private CoapServer server;
    private RequestLink requestLink;

    public void start(final RequestLink requestLink, final Settings settings) {
        this.requestLink = requestLink;
        server = new CatchallServer();
        server.addEndpoint(new CoapEndpoint(settings.get(Settings.SERVER_PORT)));
        server.start();
    }

    public void stop() {
        server.destroy();
        server = null;
    }

    private class CatchallServer extends CoapServer {
        @Override
        protected Resource createRoot() {
            return new CatchallResource();
        }
    }

    private class CatchallResource extends CoapResource {
        public CatchallResource() {
            super("");
        }

        @Override
        public void handleRequest(Exchange exchange) {
            final CoapExchange ex = new CoapExchange(exchange, this);

            final CoAP.Code code = ex.getRequestCode();
            if (code == null) {
                return;
            }

            final OptionSet opts = ex.getRequestOptions();
            final String qs = opts.getUriQueryString();
            final String uri = opts.getUriPathString() + ("".equals(qs) ? "" : "?"+qs);

            // Prepare the internal request
            final GwRequest gw_request = new GwRequest();
            final GwHeaders h = gw_request.headers();
            h.set(GW_PATH, uri);
            final CoAP.Type type = exchange.getRequest().getType();
            h.set(GW_QOS, (byte) (type == CoAP.Type.CON ? 1 : 0));

            // Set headers according to the request code
            switch (code) {
                case GET:
                    h.set(GW_OPERATION, Operation.READ);
                    if (opts.hasAccept())
                        h.set(GW_EXPECTED_TYPES, MediaTypeRegistry.toString(opts.getAccept()));
                    break;
                case PUT:
                    h.set(GW_OPERATION, Operation.UPDATE);
                case POST:
                    h.setIfAbsent(GW_OPERATION, Operation.CREATE);
                    if (opts.hasContentFormat())
                        h.set(GW_CONTENT_TYPE, MediaTypeRegistry.toString(opts.getContentFormat()));
                    gw_request.payload().set(ex.getRequestPayload());
                    break;
                case DELETE:
                    h.set(GW_OPERATION, Operation.DELETE);
                    break;
            }

            // Make the internal request
            final FutureReply future = requestLink.send(gw_request);
            try {
                final GwReply gw_reply = future.get(1, TimeUnit.MINUTES);

                switch (code) {
                    case GET:
                        final byte[] payload = gw_reply.payload().asBytes();
                        final int contentFormat = MediaTypeRegistry.parse(gw_reply.headers().get(GW_CONTENT_TYPE));
                        ex.respond(ResponseCode.CONTENT, payload, contentFormat);
                        break;
                    case POST:
                        ex.respond(ResponseCode.CREATED);
                        break;
                    case PUT:
                        ex.respond(ResponseCode.CHANGED);
                        break;
                    case DELETE:
                        ex.respond(ResponseCode.DELETED);
                        break;
                }
            }
            // handle possible errors
            catch (InterruptedException e) {
                ex.respond(ResponseCode.SERVICE_UNAVAILABLE);
            } catch (ExecutionException e) {
                if (e.getCause() instanceof GatewayException) {
                    final GatewayException cause = (GatewayException) e.getCause();
                    switch (cause.getErrorMessage().getCode()) {
                        case INVALID_URI:
                            ex.respond(ResponseCode.BAD_REQUEST);
                            return;
                        case UNAVAILABLE_PLUGIN:
                        case TARGET_NOT_FOUND:
                        case PATH_NOT_FOUND:
                            ex.respond(ResponseCode.NOT_FOUND);
                            return;
                    }
                }
                ex.respond(ResponseCode.INTERNAL_SERVER_ERROR);
            } catch (TimeoutException e) {
                ex.respond(ResponseCode.GATEWAY_TIMEOUT);
            }
        }

        @Override
        public Resource getChild(String name) {
            return this;
        }
    }
}
