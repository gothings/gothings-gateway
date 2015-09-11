package br.ufs.gothings.plugins.http;

import br.ufs.gothings.core.Settings;
import br.ufs.gothings.core.message.sink.MessageLink;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;

/**
 * @author Wagner Macedo
 */
@Deprecated
final class NettyServer implements HttpPluginServer {

    private MessageLink messageLink;

    private Channel channel;
    private NioEventLoopGroup bossGroup;
    private NioEventLoopGroup workerGroup;

    @Override
    public void start(final MessageLink messageLink, final Settings settings) throws InterruptedException {
        if (this.messageLink != null) {
            throw new IllegalStateException("Server already started");
        }

        this.messageLink = messageLink;

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        final ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                 .channel(NioServerSocketChannel.class)
                 .childHandler(new NettyServerInitializer(messageLink));

        // get server port from settings
        int port = settings.get(Settings.SERVER_PORT);

        final ChannelFuture bind = bootstrap.bind(port);
        channel = bind.channel();
        try {
            bind.sync();
        } catch (InterruptedException e) {
            stop();
            throw e;
        }
    }

    @Override
    public void stop() throws InterruptedException {
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
            messageLink = null;
            channel = null;
            bossGroup = null;
            workerGroup = null;
        }
    }

    @Deprecated
    private static final class NettyServerInitializer extends ChannelInitializer<Channel> {
        private final MessageLink messageLink;

        public NettyServerInitializer(MessageLink messageLink) {
            this.messageLink = messageLink;
        }

        @Override
        protected void initChannel(Channel ch) throws Exception {
            final ChannelPipeline pipeline = ch.pipeline();
            pipeline.addLast(new HttpServerCodec())
                    .addLast(new HttpObjectAggregator(512 * 1024))
                    .addLast(new NettyServerHandler(messageLink));
        }
    }
}
