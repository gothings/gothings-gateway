package br.ufs.gothings.plugins.http;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;

import java.net.InetSocketAddress;

import static io.netty.handler.codec.http.HttpResponseStatus.CONTINUE;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * @author Wagner Macedo
 */
final class HttpPluginServer {
    private Channel channel;
    private NioEventLoopGroup bossGroup;
    private NioEventLoopGroup workerGroup;

    void start() throws InterruptedException {
        if (channel != null) {
            throw new IllegalStateException("Server already started");
        }

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        final ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                 .channel(NioServerSocketChannel.class)
                 .childHandler(new HttpServerInitializer(this));

        final ChannelFuture bind = bootstrap.bind(8080);
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
