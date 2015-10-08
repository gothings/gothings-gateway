package br.ufs.gothings.plugins.http;

import br.ufs.gothings.core.Settings;
import br.ufs.gothings.core.common.GatewayException;
import br.ufs.gothings.core.message.GwHeaders;
import br.ufs.gothings.core.message.GwReply;
import br.ufs.gothings.core.message.GwRequest;
import br.ufs.gothings.core.message.headers.Operation;
import br.ufs.gothings.core.plugin.RequestLink;
import org.apache.http.*;
import org.apache.http.config.SocketConfig;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.apache.http.protocol.*;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * @author Wagner Macedo
 */
public class ApacheHCServer implements HttpPluginServer {

    private HttpServer server;

    @Override
    public void start(final RequestLink requestLink, final Settings settings) throws InterruptedException {
        final HttpRequestHandler requestHandler = new ServerRequestHandler(requestLink);
        final HttpProcessor httpProcessor = HttpProcessorBuilder.create()
                .add(new ResponseDate())
                .add(new ResponseContent())
                .add(new ResponseConnControl())
                .build();
        server = ServerBootstrap.bootstrap()
                .setListenerPort(settings.get(Settings.SERVER_PORT))
                .setHttpProcessor(httpProcessor)
                .setSocketConfig(SocketConfig.DEFAULT)
                .registerHandler("*", requestHandler)
                .create();
        try {
            server.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void stop() throws InterruptedException {
        server.shutdown(5, TimeUnit.SECONDS);
    }

    static class ServerRequestHandler implements HttpRequestHandler {
        private final RequestLink requestLink;

        public ServerRequestHandler(final RequestLink requestLink) {
            this.requestLink = requestLink;
        }

        @Override
        public void handle(final HttpRequest request, final HttpResponse response, final HttpContext context) throws HttpException {
            final GwRequest gw_request;
            try {
                gw_request = parseHttpRequest(request);
            } catch (IOException e) {
                response.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
                return;
            }

            if (gw_request != null) {
                final Future<GwReply> future = requestLink.send(gw_request);
                try {
                    final GwReply gw_reply = future.get(1, TimeUnit.MINUTES);

                    final BasicHttpEntity entity = new BasicHttpEntity();
                    entity.setContent(gw_reply.payload().asInputStream());
                    response.setEntity(entity);
                    fillHttpResponseHeaders(response, gw_reply.headers());
                }
                // handle possible errors
                catch (InterruptedException e) {
                    response.setStatusCode(HttpStatus.SC_SERVICE_UNAVAILABLE);
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof GatewayException) {
                        final GatewayException cause = (GatewayException) e.getCause();
                        switch (cause.getErrorMessage().getReason()) {
                            case INVALID_URI:
                                response.setStatusCode(HttpStatus.SC_BAD_REQUEST);
                                break;
                            case UNAVAILABLE_PLUGIN:
                            case TARGET_NOT_FOUND:
                            case PATH_NOT_FOUND:
                                response.setStatusCode(HttpStatus.SC_NOT_FOUND);
                                break;
                            default:
                                response.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
                        }
                    }
                } catch (TimeoutException e) {
                    response.setStatusCode(HttpStatus.SC_GATEWAY_TIMEOUT);
                }
            } else {
                response.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
            }
        }

        private static GwRequest parseHttpRequest(final HttpRequest request) throws IOException {
            final String method = request.getRequestLine().getMethod();
            switch (method) {
                case "GET":
                case "PUT":
                case "POST":
                case "DELETE":
                    final GwRequest msg = new GwRequest();
                    final GwHeaders h = msg.headers();
                    h.setPath(request.getRequestLine().getUri());

                    switch (method) {
                        case "GET":
                            h.setOperation(Operation.READ);
                            addExpectedTypes(h, request.getFirstHeader("Accept"));
                            break;
                        case "PUT":
                            h.setOperation(Operation.UPDATE);
                        case "POST":
                            if (h.getOperation() == null) {
                                h.setOperation(Operation.CREATE);
                            }
                            if (request instanceof HttpEntityEnclosingRequest) {
                                final HttpEntity entity = ((HttpEntityEnclosingRequest) request).getEntity();
                                msg.payload().set(entity.getContent());
                            }
                            final Header ct = request.getFirstHeader("Content-Type");
                            addGatewayHeader(h::setContentType, ct);
                            break;
                        case "DELETE":
                            h.setOperation(Operation.DELETE);
                            break;
                    }

                    return msg;
            }
            return null;
        }

        private static void addExpectedTypes(final GwHeaders gw_headers, final Header acceptHeader) {
            if (acceptHeader != null) {
                final String acceptValues = acceptHeader.getValue();
                for (String type : acceptValues.split(",")) {
                    final int pos = type.indexOf(';');
                    gw_headers.addExpectedType(pos != -1 ? type.substring(0, pos) : type);
                }
            }
        }

        private static void addGatewayHeader(final Consumer<String> addFunction, final Header httpHeader) {
            if (httpHeader != null) {
                addFunction.accept(httpHeader.getValue());
            }
        }

        private void fillHttpResponseHeaders(final HttpResponse response, final GwHeaders gwh) {
            addHttpHeader(response, "Content-Type", gwh.getContentType());
        }

        private static void addHttpHeader(HttpResponse response, String name, CharSequence value) {
            if (value != null) {
                response.addHeader(name, String.valueOf(value));
            }
        }
    }
}
