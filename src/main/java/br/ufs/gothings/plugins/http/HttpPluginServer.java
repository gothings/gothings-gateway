package br.ufs.gothings.plugins.http;

import br.ufs.gothings.core.CommunicationManager;
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
                 .childHandler(new HttpPluginServerInitializer(manager));

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

    private static final class HttpPluginServerInitializer extends ChannelInitializer<Channel> {
        private final CommunicationManager manager;

        public HttpPluginServerInitializer(CommunicationManager manager) {
            this.manager = manager;
        }

        @Override
        protected void initChannel(Channel ch) throws Exception {
            final ChannelPipeline pipeline = ch.pipeline();
            pipeline.addLast(new HttpServerCodec())
                    .addLast(new HttpObjectAggregator(512 * 1024))
                    .addLast(new HttpPluginServerHandler(manager));
        }
    }
}
