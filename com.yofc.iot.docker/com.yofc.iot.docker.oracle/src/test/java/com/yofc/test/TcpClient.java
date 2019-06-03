package com.yofc.test;

import java.util.concurrent.TimeUnit;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.ReferenceCountUtil;
import io.vertx.core.Handler;

public class TcpClient {

    private TcpClientHandler clientHandler = new TcpClientHandler();
    private Handler<ByteBuf> handler;

    public void connect(String host, int port) {
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(workerGroup);
            bootstrap.channel(NioSocketChannel.class);
            bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
            bootstrap.handler(new ChannelInitializer<SocketChannel>() {

                @Override
                public void initChannel(SocketChannel ch) throws Exception {
                    ch.pipeline().addLast(clientHandler);
                }
            });
            clientHandler.setHandler(this::handlerBuffer);
            bootstrap.connect(host, port).sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    private void handlerBuffer(ByteBuf buffer) {
        int head = buffer.getUnsignedShortLE(0);
        if (head == 10011) {
            try {
                ByteBuf buf = Unpooled.buffer();
                buf.writeBytes(buffer);
                write(buf);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (handler != null)
            handler.handle(buffer);
        ReferenceCountUtil.release(buffer);
    }

    public void addHandle(Handler<ByteBuf> handler) {
        this.handler = handler;
    }

    public ByteBuf sendAndReply(ByteBuf message) throws InterruptedException {
        ChannelPromise promise = clientHandler.sendAndGetMessage(message);
        promise.await(2000, TimeUnit.MILLISECONDS);
        return clientHandler.getData();
    }

    public void write(ByteBuf message) throws InterruptedException {
        clientHandler.sendMessage(message);
    }
}
