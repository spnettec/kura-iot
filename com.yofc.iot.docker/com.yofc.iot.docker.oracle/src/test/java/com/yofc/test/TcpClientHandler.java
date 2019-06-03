package com.yofc.test;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.vertx.core.Handler;

public class TcpClientHandler extends ChannelInboundHandlerAdapter {

    private ChannelHandlerContext ctx;
    private ChannelPromise promise;
    private ByteBuf data;
    private Handler<ByteBuf> handler;

    public void setHandler(Handler<ByteBuf> handler) {
        this.handler = handler;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        this.ctx = ctx;
    }

    public ChannelPromise sendAndGetMessage(ByteBuf message) {
        if (ctx == null)
            throw new IllegalStateException();
        promise = ctx.writeAndFlush(message).channel().newPromise();
        return promise;
    }

    public void sendMessage(ByteBuf message) {
        if (ctx == null)
            throw new IllegalStateException();
        ctx.writeAndFlush(message);
    }

    public ByteBuf getData() {
        return data;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        System.err.println("客户端读取到数据");
        data = (ByteBuf) msg;
        if (!promise.isDone())
            promise.setSuccess();

    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
        if (this.handler != null)
            this.handler.handle(data);
        System.err.println("客户端读取数据完毕");
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        System.err.println("client 读取数据出现异常");
        ctx.close();
    }
}
