package com.yofc.test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.HashedWheelTimer;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.Promise;

public class NettyTcpClient {

    private NettyTcpClientHandler clientHandler = new NettyTcpClientHandler();
    private Consumer<ByteBuf> handler;
    private HashedWheelTimer timer = new HashedWheelTimer();

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
        buffer.resetReaderIndex();
        if (head == 10011) {
            try {
                write(buffer.copy());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (handler != null)
            handler.accept(buffer);
    }

    public void addHandle(Consumer<ByteBuf> handler) {
        this.handler = handler;
    }

    public void sendAndReply(ByteBuf message, long timeOut, Consumer<Promise<ByteBuf>> handler)
            throws InterruptedException, TimeoutException {
        CompletableFuture<ByteBuf> future = clientHandler.sendAndGetMessage(message);
        timer.newTimeout(new TimerTask() {

            public void run(Timeout timeout) throws Exception {
                if (!future.isDone())
                    future.completeExceptionally(new TimeoutException());
            }
        }, timeOut, TimeUnit.MILLISECONDS);
        future.whenComplete((msg, ex) -> {
            DefaultPromise<ByteBuf> pomis = new DefaultPromise<ByteBuf>(GlobalEventExecutor.INSTANCE);
            if (ex == null)
                pomis.setSuccess(msg);
            else
                pomis.setFailure(ex);
            try {
                handler.accept(pomis);
            } finally {
                ReferenceCountUtil.release(msg);
            }
        });

    }

    public void write(ByteBuf message) throws InterruptedException {
        clientHandler.sendMessage(message);
    }

    public ByteBuf get10003DataBuf(int orderNo, int orderStep, int taskType, int productType, int priority,
            int vehicleType, int vehicleNo, int nextTag, int paraNum, int paraPosRecv, int paraOpRecv, int paraPosSend,
            int paraOpSend) {
        ByteBuf buf = Unpooled.buffer();
        buf.writeShortLE(10003);
        buf.writeIntLE(orderNo);
        buf.writeShortLE(orderStep);
        buf.writeShortLE(taskType);
        buf.writeShortLE(productType);
        buf.writeShortLE(priority);
        buf.writeShortLE(vehicleType);
        buf.writeShortLE(vehicleNo);
        buf.writeShortLE(nextTag);
        buf.writeShortLE(paraNum);
        buf.writeShortLE(paraPosRecv);
        buf.writeShortLE(paraOpRecv);
        buf.writeShortLE(paraPosSend);
        buf.writeShortLE(paraOpSend);
        buf.writeShortLE(checkSum(buf));
        return buf;
    }

    private static int checkSum(ByteBuf buf) {
        int sum = 0;
        if (buf != null) {
            for (int i = 0; i < buf.writerIndex(); i++) {
                sum += buf.getByte(i);
            }
        }
        return sum & 0xff;
    }
}
