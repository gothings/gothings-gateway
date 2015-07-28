package br.ufs.gothings.plugins.http;

import br.ufs.gothings.core.CommunicationManager;
import br.ufs.gothings.core.GwHeaders;
import br.ufs.gothings.core.GwMessage;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaders.Values.KEEP_ALIVE;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * @author Wagner Macedo
 */
final class HttpPluginServer {
    private CommunicationManager manager;

    private Channel channel;
    private NioEventLoopGroup bossGroup;
    private NioEventLoopGroup workerGroup;

    void start(final CommunicationManager manager, final int port) throws InterruptedException {
        if (this.manager != null) {
            throw new IllegalStateException("Server already started");
        }

        this.manager = manager;

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        final ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                 .channel(NioServerSocketChannel.class)
                 .childHandler(new HttpServerInitializer(this));

        final ChannelFuture bind = bootstrap.bind(port);
        channel = bind.channel();
        try {
            bind.sync();
        } catch (InterruptedException e) {
            stop();
            throw e;
        }
    }

    void stop() throws InterruptedException {
        try {
            // Ask to close
            channel.close();
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
            // Wait to close
            channel.closeFuture().sync();
            bossGroup.terminationFuture().sync();
            workerGroup.terminationFuture().sync();
        } finally {
            manager = null;
            channel = null;
            bossGroup = null;
            workerGroup = null;
        }
    }

    /* To be used in unit tests */
    int getPort() {
        return ((InetSocketAddress) channel.localAddress()).getPort();
    }

    private void forwardRequest(ChannelHandlerContext ctx, FullHttpRequest request) {
        final GwMessage gw_request;
        switch (request.getMethod().name()) {
            case "GET":
                gw_request = null;
                break;
            case "PUT":
                gw_request = null;
                break;
            case "POST":
                gw_request = null;
                break;
            case "DELETE":
                gw_request = null;
                break;
            default:
                ctx.writeAndFlush(new DefaultFullHttpResponse(request.getProtocolVersion(), METHOD_NOT_ALLOWED));
                gw_request = null;
        }

        final FullHttpResponse response = new DefaultFullHttpResponse(request.getProtocolVersion(), OK);
        GwMessage gw_response = null;
        if (gw_request != null) {
            final Future<GwMessage> f = manager.sendRequest(gw_request);
            try {
                gw_response = f.get(1, TimeUnit.MINUTES);
            } catch (InterruptedException | ExecutionException e) {
                response.setStatus(INTERNAL_SERVER_ERROR);
            } catch (TimeoutException e) {
                response.setStatus(REQUEST_TIMEOUT);
            }
        }

        if (gw_response != null) {
            response.content().writeBytes(gw_response.payload());
            fillHttpHeaders(response.headers(), gw_response.headers());
        }

        final boolean keepAlive = HttpHeaders.isKeepAlive(request);
        if (keepAlive) {
            response.headers().set(CONNECTION, KEEP_ALIVE);
        }

        final ChannelFuture future = ctx.writeAndFlush(response);
        if (!keepAlive) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }

    private static void fillHttpHeaders(HttpHeaders http, GwHeaders gateway) {

    }

    private static final class HttpServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
        private final HttpPluginServer server;

        public HttpServerHandler(HttpPluginServer server) {
            this.server = server;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
            if (HttpHeaders.is100ContinueExpected(request)) {
                send100Continue(ctx);
            }

            server.forwardRequest(ctx, request);
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

    private static final class HttpServerInitializer extends ChannelInitializer<ServerChannel> {
        private final HttpPluginServer server;

        public HttpServerInitializer(HttpPluginServer server) {
            this.server = server;
        }

        @Override
        protected void initChannel(ServerChannel ch) throws Exception {
            final ChannelPipeline pipeline = ch.pipeline();
            pipeline.addLast(new HttpServerCodec())
                    .addLast(new HttpObjectAggregator(512 * 1024))
                    .addLast(new HttpServerHandler(server));
        }
    }
}
