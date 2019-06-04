package com.yofc.test;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class NettyTcpClientHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private ChannelHandlerContext ctx;
    private CompletableFuture<ByteBuf> future;
    private Consumer<ByteBuf> handler;
    private static final Logger logger = LoggerFactory.getLogger(SimpleChannelInboundHandler.class);

    public void setHandler(Consumer<ByteBuf> handler) {
        this.handler = handler;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        this.ctx = ctx;
    }

    public CompletableFuture<ByteBuf> sendAndGetMessage(ByteBuf message) {
        if (ctx == null)
            throw new IllegalStateException();
        future = new CompletableFuture<>();
        ctx.writeAndFlush(message);
        return this.future;
    }

    public void sendMessage(ByteBuf message) {
        if (ctx == null)
            throw new IllegalStateException();
        ctx.writeAndFlush(message);
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        if (future != null && !future.isDone()) {
            future.complete(msg.copy());
        }
        if (this.handler != null) {
            this.handler.accept(msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("客户端读取数据出现异常", cause);
        ctx.close();
    }

}
