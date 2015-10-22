package br.ufs.gothings.plugins.http;

import br.ufs.gothings.core.message.headers.GwHeaders;
import br.ufs.gothings.core.Settings;
import br.ufs.gothings.core.message.GwReply;
import br.ufs.gothings.core.message.GwRequest;
import br.ufs.gothings.core.message.headers.Operation;
import br.ufs.gothings.core.plugin.RequestLink;
import br.ufs.gothings.core.common.GatewayException;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.Status;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static br.ufs.gothings.core.message.headers.HeaderNames.*;

/**
 * @author Wagner Macedo
 */
@Deprecated
class NanoHTTPDServer implements HttpPluginServer {

    private Server server;

    public void start(final RequestLink requestLink, final Settings settings) throws InterruptedException{
        server = new Server(requestLink, settings.get(Settings.SERVER_PORT));
        try {
            server.start();
        } catch (IOException e) {
            throw new UnknownError();
        }
    }

    public void stop() throws InterruptedException {
        server.stop();
    }

    static final class Server extends NanoHTTPD {
        private final RequestLink requestLink;

        public Server(final RequestLink requestLink, final int port) {
            super(port);
            this.requestLink = requestLink;
        }

        @Override
        public Response serve(final IHTTPSession session) {
            final GwRequest gw_request;
            try {
                gw_request = parseHttpRequest(session);
            } catch (IOException e) {
                return new Response(Status.INTERNAL_ERROR, null, "");
            }

            if (gw_request != null) {
                final Future<GwReply> future = requestLink.send(gw_request);
                try {
                    final GwReply gw_reply = future.get(1, TimeUnit.MINUTES);

                    final Response http_response = new Response(Status.OK, null, "");
                    http_response.setData(gw_reply.payload().asInputStream());
                    fillHttpResponseHeaders(http_response, gw_reply.headers());

                    return http_response;
                }
                // handle possible errors
                catch (InterruptedException | ExecutionException | TimeoutException e) {
                    Status status = Status.INTERNAL_ERROR;

                    if (e.getCause() instanceof GatewayException) {
                        final GatewayException gwx = (GatewayException) e.getCause();
                        switch (gwx.getErrorMessage().getReason()) {
                            case INVALID_URI:
                                status = Status.BAD_REQUEST;
                                break;
                            case UNAVAILABLE_PLUGIN:
                            case TARGET_NOT_FOUND:
                            case PATH_NOT_FOUND:
                                status = Status.NOT_FOUND;
                                break;
                        }
                    }

                    return new Response(status, null, "");
                }
            }

            // If no request was created then the used HTTP method is not allowed
            return new Response(Status.METHOD_NOT_ALLOWED, null, "");
        }

        private static GwRequest parseHttpRequest(final IHTTPSession session) throws IOException {
            final Method method = session.getMethod();
            switch (method) {
                case GET: case PUT: case POST: case DELETE:
                    final GwRequest msg = new GwRequest();

                    // filling GwHeaders
                    final GwHeaders h = msg.headers();
                    h.set(GW_PATH, session.getUri());

                    // get http headers
                    final Map<String, String> sessionHeaders = session.getHeaders();

                    switch (method) {
                        case GET:
                            h.set(GW_OPERATION, Operation.READ);
                            addExpectedTypes(h, sessionHeaders);
                            break;
                        case PUT:
                            msg.payload().set(session.getInputStream());
                            h.set(GW_OPERATION, Operation.UPDATE);
                            h.set(GW_CONTENT_TYPE, sessionHeaders.get("content-type"));
                            break;
                        case POST:
                            msg.payload().set(session.getInputStream());
                            h.set(GW_OPERATION, Operation.CREATE);
                            h.set(GW_CONTENT_TYPE, sessionHeaders.get("content-type"));
                            break;
                        case DELETE:
                            h.set(GW_OPERATION, Operation.DELETE);
                            break;
                    }

                    return msg;

                default:
                    return null;
            }
        }

        private static void addExpectedTypes(final GwHeaders gw_headers, final Map<String, String> headers) {
            final String acceptValues = headers.get("accept");
            if (acceptValues != null) {
                for (String type : acceptValues.split(",")) {
                    final int pos = type.indexOf(';');
                    gw_headers.add(GW_EXPECTED_TYPES, pos != -1 ? type.substring(0, pos) : type);
                }
            }
        }

        private static void fillHttpResponseHeaders(Response response, GwHeaders gwh) {
            addHttpHeader(response, "content-type", gwh.get(GW_CONTENT_TYPE));
        }

        private static void addHttpHeader(Response response, String key, CharSequence value) {
            if (value != null) {
                response.addHeader(key, String.valueOf(value));
            }
        }
    }
}
