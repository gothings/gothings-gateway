package br.ufs.gothings.plugins.http;

import br.ufs.gothings.core.GwHeaders;
import br.ufs.gothings.core.GwMessage;
import br.ufs.gothings.core.Settings;
import br.ufs.gothings.core.message.headers.ComplexHeader;
import br.ufs.gothings.core.message.headers.Operation;
import br.ufs.gothings.core.message.sink.MessageLink;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.Status;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author Wagner Macedo
 */
class NanoHTTPDServer implements HttpPluginServer {

    private Server server;

    public void start(final MessageLink messageLink, final Settings settings) throws InterruptedException{
        server = new Server(messageLink, settings.get(Settings.SERVER_PORT));
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
        private final MessageLink messageLink;

        public Server(final MessageLink messageLink, final int port) {
            super(port);
            this.messageLink = messageLink;
            this.messageLink.setUp(null);
        }

        @Override
        public Response serve(final IHTTPSession session) {
            final GwMessage gw_request;
            try {
                gw_request = parseHttpRequest(session);
            } catch (IOException e) {
                return new Response(Status.INTERNAL_ERROR, null, "");
            }

            if (gw_request != null) {
                final Future<GwMessage> future = messageLink.send(gw_request);
                try {
                    final GwMessage gw_response = future.get(1, TimeUnit.MINUTES);

                    final Response http_response = new Response(Status.OK, null, "");
                    http_response.setData(gw_response.payload().asInputStream());
                    fillHttpResponseHeaders(http_response, gw_response.headers());

                    return http_response;
                }
                // handle possible errors
                catch (InterruptedException | ExecutionException | TimeoutException e) {
                    return new Response(Status.INTERNAL_ERROR, null, "");
                }
            }

            // If no request was created then the used HTTP method is not allowed
            return new Response(Status.METHOD_NOT_ALLOWED, null, "");
        }

        private static GwMessage parseHttpRequest(final IHTTPSession session) throws IOException {
            final Method method = session.getMethod();
            switch (method) {
                case GET: case PUT: case POST: case DELETE:
                    final GwMessage msg = GwMessage.newMessage();

                    // filling GwHeaders
                    final GwHeaders h = msg.headers();
                    h.pathHeader().set(session.getUri());

                    // get http headers
                    final Map<String, String> sessionHeaders = session.getHeaders();

                    switch (method) {
                        case GET:
                            h.operationHeader().set(Operation.READ);
                            addExpectedTypes(h, sessionHeaders);
                            break;
                        case PUT:
                            msg.payload().set(session.getInputStream());
                            h.operationHeader().set(Operation.UPDATE);
                            h.contentTypeHeader().set(sessionHeaders.get("content-type"));
                            break;
                        case POST:
                            msg.payload().set(session.getInputStream());
                            h.operationHeader().set(Operation.CREATE);
                            h.contentTypeHeader().set(sessionHeaders.get("content-type"));
                            break;
                        case DELETE:
                            h.operationHeader().set(Operation.DELETE);
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
                final ComplexHeader<String> expectedTypes = gw_headers.expectedTypesHeader();
                for (String type : acceptValues.split(",")) {
                    final int pos = type.indexOf(';');
                    expectedTypes.add(pos != -1 ? type.substring(0, pos) : type);
                }
            }
        }

        private static void fillHttpResponseHeaders(Response response, GwHeaders gwh) {
            addHttpHeader(response, "content-type", gwh.contentTypeHeader().get());
        }

        private static void addHttpHeader(Response response, String key, CharSequence value) {
            if (value != null) {
                response.addHeader(key, String.valueOf(value));
            }
        }
    }
}
