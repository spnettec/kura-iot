package com.yofc.netty.fsm.tcp;

import java.util.List;
import java.util.concurrent.ExecutorService;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;

public class NettyTcpClientHandler extends ByteToMessageCodec<Command> {

    private final ExecutorService executor;

    private final NettyTcpClient client;

    NettyTcpClientHandler(NettyTcpClient client) {
        this.client = client;

        executor = client.getConfig().getExecutor();
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

    @Override
    protected void encode(ChannelHandlerContext ctx, Command msg, ByteBuf out) throws Exception {
        out.writeBytes(msg.getPayload());
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        out.add(new Command(in));
    }

}
