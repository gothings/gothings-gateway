package br.ufs.gothings.plugins.http;

import br.ufs.gothings.core.CommunicationManager;
import br.ufs.gothings.core.GwHeaders;
import br.ufs.gothings.core.GwMessage;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;

import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io.netty.handler.codec.http.HttpHeaders.Names.ACCEPT;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaders.Values.KEEP_ALIVE;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * @author Wagner Macedo
 */
final class HttpPluginServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private final CommunicationManager manager;

    HttpPluginServerHandler(CommunicationManager manager) {
        this.manager = manager;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        if (HttpHeaders.is100ContinueExpected(request)) {
            send100Continue(ctx);
        }

        final FullHttpResponse response = new DefaultFullHttpResponse(request.getProtocolVersion(), OK);

        final GwMessage gw_request = parseHttpRequest(request);
        if (gw_request != null) {
            final Future<GwMessage> f = manager.sendRequest(gw_request);
            try {
                final GwMessage gw_response = f.get(1, TimeUnit.MINUTES);
                response.content().writeBytes(gw_response.payload());
                fillHttpResponseHeaders(response.headers(), gw_response.headers());
            }
            // Handling with possible errors
            catch (InterruptedException e) {
                ctx.close();
                return;
            } catch (ExecutionException e) {
                e.printStackTrace();
                response.setStatus(INTERNAL_SERVER_ERROR);
            } catch (TimeoutException e) {
                response.setStatus(REQUEST_TIMEOUT);
            }
        }
        // If no request was created then the used HTTP method is not allowed
        else {
            response.setStatus(METHOD_NOT_ALLOWED);
        }

        // Handling keep-alive setting
        final boolean keepAlive = HttpHeaders.isKeepAlive(request);
        if (keepAlive) {
            response.headers().set(CONNECTION, KEEP_ALIVE);
        }

        final ChannelFuture future = ctx.writeAndFlush(response);
        if (!keepAlive) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }

    private static GwMessage parseHttpRequest(FullHttpRequest request) {
        final HttpHeaders headers = request.headers();

        final String method = request.getMethod().name();
        switch (method) {
            case "GET":case "PUT":case "POST":case "DELETE":
                final GwMessage msg = new GwMessage();
                final GwHeaders gw_headers = msg.headers();
                gw_headers.setPath(request.getUri());

                switch (method) {
                    case "GET":
                        gw_headers.setOperation("GET");
                        addExpectedTypes(gw_headers, headers);
                        break;
                    case "PUT":
                        gw_headers.setOperation("PUT");
                        gw_headers.setForwardType(headers.get(CONTENT_TYPE));
                        msg.setPayload(request.content());
                        break;
                    case "POST":
                        gw_headers.setOperation("POST");
                        gw_headers.setForwardType(headers.get(CONTENT_TYPE));
                        msg.setPayload(request.content());
                        break;
                    case "DELETE":
                        gw_headers.setOperation("DELETE");
                        break;
                }

                return msg;
        }
        return null;
    }

    private static void addExpectedTypes(GwHeaders gw_headers, HttpHeaders headers) {
        final String accept = headers.get(ACCEPT);
        if (accept != null) {
            final Set<String> expectedTypes = gw_headers.expectedTypes();
            for (String type : accept.split(",")) {
                final int pos = type.indexOf(';');
                expectedTypes.add(pos != -1 ? type.substring(0, pos) : type);
            }
        }
    }

    private static void fillHttpResponseHeaders(HttpHeaders http, GwHeaders gateway) {

    }

    private static void send100Continue(ChannelHandlerContext ctx) {
        final FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, CONTINUE);
        ctx.writeAndFlush(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
