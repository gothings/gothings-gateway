package br.ufs.gothings.plugins.http;

import br.ufs.gothings.core.GwMessage;
import br.ufs.gothings.core.sink.Sink;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;

/**
 * @author Wagner Macedo
 */
final class HttpPluginServer {
    private Sink<GwMessage> sink;

    private Channel channel;
    private NioEventLoopGroup bossGroup;
    private NioEventLoopGroup workerGroup;

    void start(final Sink<GwMessage> sink, final int port) throws InterruptedException {
        if (this.sink != null) {
            throw new IllegalStateException("Server already started");
        }

        this.sink = sink;

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        final ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                 .channel(NioServerSocketChannel.class)
                 .childHandler(new HttpPluginServerInitializer(sink));

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
            sink = null;
            channel = null;
            bossGroup = null;
            workerGroup = null;
        }
    }

    private static final class HttpPluginServerInitializer extends ChannelInitializer<Channel> {
        private final Sink<GwMessage> sink;

        public HttpPluginServerInitializer(Sink<GwMessage> sink) {
            this.sink = sink;
        }

        @Override
        protected void initChannel(Channel ch) throws Exception {
            final ChannelPipeline pipeline = ch.pipeline();
            pipeline.addLast(new HttpServerCodec())
                    .addLast(new HttpObjectAggregator(512 * 1024))
                    .addLast(new HttpPluginServerHandler(sink));
        }
    }
}
