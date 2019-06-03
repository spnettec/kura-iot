package com.yofc.test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

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

public class TcpClient {

    private TcpClientHandler clientHandler = new TcpClientHandler();
    private Consumer<ByteBuf> handler;

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

    public ByteBuf sendAndReply(ByteBuf message) throws InterruptedException, TimeoutException {
        ChannelPromise promise = clientHandler.sendAndGetMessage(message);
        boolean result = promise.await(2000, TimeUnit.MILLISECONDS);
        if (!result)
            throw new TimeoutException();
        return clientHandler.getData();
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
