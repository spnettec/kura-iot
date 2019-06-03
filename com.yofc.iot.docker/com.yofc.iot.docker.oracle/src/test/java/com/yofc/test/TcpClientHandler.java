package com.yofc.test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.vertx.core.Handler;

public class TcpClientHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private ChannelHandlerContext ctx;
    private ChannelPromise promise;
    private ByteBuf data;
    private Handler<ByteBuf> handler;
    private static final Logger logger = LoggerFactory.getLogger(SimpleChannelInboundHandler.class);

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
    public void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        logger.info("客户端读取到数据");

        if (promise != null && !promise.isDone()) {
            data = msg.copy();
            promise.setSuccess();
        }
        if (this.handler != null) {
            this.handler.handle(msg.copy());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("客户端读取数据出现异常", cause);
        ctx.close();
    }

}
