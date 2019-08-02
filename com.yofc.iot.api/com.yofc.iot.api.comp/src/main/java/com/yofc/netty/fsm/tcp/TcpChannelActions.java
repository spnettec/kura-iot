package com.yofc.netty.fsm.tcp;

import java.util.concurrent.CompletableFuture;

import com.yofc.netty.fsm.ChannelActions;
import com.yofc.netty.fsm.Event;
import com.yofc.netty.fsm.State;
import com.yofc.state.machine.FsmContext;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.internal.PlatformDependent;

public class TcpChannelActions implements ChannelActions {

    private final NettyTcpClient client;

    public TcpChannelActions(NettyTcpClient client) {
        this.client = client;
    }

    @Override
    public CompletableFuture<Channel> connect(FsmContext<State, Event> ctx) {
        return bootstrap(this.client).thenCompose(channel -> {
            CompletableFuture<Void> future = new CompletableFuture<>();
            client.onConnected(future);
            return future.thenApply(rs -> channel);
        });
    }

    @Override
    public CompletableFuture<Void> disconnect(FsmContext<State, Event> ctx, Channel channel) {
        CompletableFuture<Void> disconnectFuture = new CompletableFuture<>();
        channel.pipeline().addFirst(new ChannelInboundHandlerAdapter() {

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                disconnectFuture.complete(null);
            }
        });

        CompletableFuture<ByteBuf> future = new CompletableFuture<>();

        future.whenComplete((cmd, ex2) -> {
            channel.close();
            disconnectFuture.complete(null);
        });

        return disconnectFuture;
    }

    private static CompletableFuture<Channel> bootstrap(NettyTcpClient client) {
        CompletableFuture<Channel> future = new CompletableFuture<>();
        TcpNetIpClientConfig config = client.getConfig();
        Bootstrap bootstrap = new Bootstrap();

        bootstrap.group(new NioEventLoopGroup()).channel(NioSocketChannel.class)
                .option(ChannelOption.ALLOCATOR, new PooledByteBufAllocator(PlatformDependent.directBufferPreferred()))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000).option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {

                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new NettyTcpClientHandler(client));
                    }
                });
        config.getBootstrapConsumer().accept(bootstrap);
        bootstrap.connect(config.getHostname(), config.getPort()).addListener((ChannelFuture f) -> {
            if (f.isSuccess()) {
                future.complete(f.channel());
            } else {
                future.completeExceptionally(f.cause());
            }
        });

        return future;
    }
}