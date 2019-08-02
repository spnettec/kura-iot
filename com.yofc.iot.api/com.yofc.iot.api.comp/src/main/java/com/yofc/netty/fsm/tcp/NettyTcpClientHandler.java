package com.yofc.netty.fsm.tcp;

import java.util.concurrent.ExecutorService;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class NettyTcpClientHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private final ExecutorService executor;

    private final NettyTcpClient client;

    NettyTcpClientHandler(NettyTcpClient client) {
        this.client = client;

        executor = client.getConfig().getExecutor();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, ByteBuf packet) {
        executor.execute(() -> client.onChannelRead(packet));
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        client.onChannelInactive(ctx);

        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        client.onExceptionCaught(ctx, cause);

        super.exceptionCaught(ctx, cause);
    }

}
