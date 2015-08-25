package br.ufs.gothings.plugins.http;

import br.ufs.gothings.core.GwHeaders;
import br.ufs.gothings.core.GwMessage;
import br.ufs.gothings.core.message.ComplexHeader;
import br.ufs.gothings.core.message.Operation;
import br.ufs.gothings.core.sink.Sink;
import br.ufs.gothings.core.sink.SinkLink;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io.netty.handler.codec.http.HttpHeaders.Names.ACCEPT;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaders.Values.KEEP_ALIVE;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * @author Wagner Macedo
 */
final class HttpPluginServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private final SinkLink<GwMessage> sinkLink;
    private final Map<Long, SynchronousQueue<GwMessage>> answers = new ConcurrentHashMap<>();

    HttpPluginServerHandler(Sink<GwMessage> sink) {
        sinkLink = sink.createLink();
        sinkLink.setListener(value -> {
            if (!value.isAnswer()) {
                return;
            }

            final SynchronousQueue<GwMessage> pipe = getPipe(value.sequence());
            pipe.put(value);
        });
    }

    private SynchronousQueue<GwMessage> getPipe(long sequence) {
        return answers.computeIfAbsent(sequence, k -> new SynchronousQueue<>());
    }

    private GwMessage getAnswer(long sequence, long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        final SynchronousQueue<GwMessage> pipe = getPipe(sequence);

        final GwMessage polled = pipe.poll(timeout, unit);
        if (polled == null) {
            throw new TimeoutException();
        } else {
            return polled;
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        if (HttpHeaders.is100ContinueExpected(request)) {
            send100Continue(ctx);
        }

        final FullHttpResponse response = new DefaultFullHttpResponse(request.getProtocolVersion(), OK);

        final GwMessage gw_request = parseHttpRequest(request);
        if (gw_request != null) {
            sinkLink.send(gw_request);
             try  {
                final GwMessage gw_response = getAnswer(gw_request.sequence(), 1, TimeUnit.MINUTES);
                response.content().writeBytes(gw_response.payload().asBuffer());
                fillHttpResponseHeaders(response.headers(), gw_response.headers());
            }
            // Handling with possible errors
            catch (InterruptedException e) {
                ctx.close();
                return;
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
                final GwMessage msg = GwMessage.newMessage();

                // filling GwHeaders
                final GwHeaders gw_headers = msg.headers();
                gw_headers.pathHeader().set(request.getUri());

                switch (method) {
                    case "GET":
                        gw_headers.operationHeader().set(Operation.GET);
                        addExpectedTypes(gw_headers, headers);
                        break;
                    case "PUT":
                        gw_headers.operationHeader().set(Operation.PUT);
                        gw_headers.contentTypeHeader().set(headers.get(CONTENT_TYPE));
                        msg.payload().set(request.content().nioBuffer());
                        break;
                    case "POST":
                        gw_headers.operationHeader().set(Operation.POST);
                        gw_headers.contentTypeHeader().set(headers.get(CONTENT_TYPE));
                        msg.payload().set(request.content().nioBuffer());
                        break;
                    case "DELETE":
                        gw_headers.operationHeader().set(Operation.DELETE);
                        break;
                }

                return msg;
        }
        return null;
    }

    private static void addExpectedTypes(GwHeaders gw_headers, HttpHeaders headers) {
        final String acceptValues = headers.get(ACCEPT);
        if (acceptValues != null) {
            final ComplexHeader<String> expectedTypes = gw_headers.expectedTypesHeader();
            for (String type : acceptValues.split(",")) {
                final int pos = type.indexOf(';');
                expectedTypes.add(pos != -1 ? type.substring(0, pos) : type);
            }
        }
    }

    private static void fillHttpResponseHeaders(HttpHeaders hh, GwHeaders gwh) {
        setHttpHeader(hh, CONTENT_TYPE, gwh.contentTypeHeader().get());
    }

    private static void setHttpHeader(HttpHeaders hh, String key, CharSequence value) {
        if (value != null) {
            hh.set(key, value);
        }
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
