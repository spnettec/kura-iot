package com.yofc.test;

import java.net.InetSocketAddress;
import java.nio.BufferOverflowException;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.HashedWheelTimer;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.TimerTask;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class NettySocketClient {

    private final String remoteHost;
    private final int remotePort;
    private EventLoopGroup workerGroup = new NioEventLoopGroup();
    private volatile Bootstrap bootstrap;
    private volatile boolean closed = false;
    private NettyClientHandler nettyClientHandler;
    private Consumer<byte[]> consumer;

    public NettySocketClient(String remoteHost, int remotePort) {
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
    }

    public Consumer<byte[]> getConsumer() {
        return consumer;
    }

    /**
     * 发送消息
     */
    public Future<byte[]> sendMessage(String msg) {
        return sendMessage(msg, 5000);
    }

    public Future<byte[]> sendMessage(String msg, long timeOut) {
        return nettyClientHandler.sendMessage(msg, timeOut);
    }

    public void addLister(Consumer<byte[]> consumer) {
        this.consumer = consumer;
    }

    public void init() {
        closed = false;
        workerGroup = new NioEventLoopGroup();
        bootstrap = new Bootstrap();
        bootstrap.group(workerGroup);
        bootstrap.channel(NioSocketChannel.class);
        bootstrap.handler(new ChannelInitializer<SocketChannel>() {

            @Override
            public void initChannel(SocketChannel ch) {
                ChannelPipeline pipeline = ch.pipeline();
                pipeline.addFirst(new ChannelInboundHandlerAdapter() {

                    @Override
                    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                        super.channelInactive(ctx);
                        ctx.channel().eventLoop().schedule(NettySocketClient.this::doConnect, 1, TimeUnit.SECONDS);
                    }
                });
            }
        });

        doConnect();
    }

    private void doConnect() {
        if (closed) {
            return;
        }
        try {
            nettyClientHandler = new NettyClientHandler(this);
            bootstrap.option(ChannelOption.SO_KEEPALIVE, true).option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<SocketChannel>() {

                        @Override
                        protected void initChannel(SocketChannel socketChannel) {
                            socketChannel.pipeline().addLast("encoder", new StringEncoder());
                            socketChannel.pipeline().addLast(nettyClientHandler);
                        }
                    }).connect(new InetSocketAddress(remoteHost, remotePort)).addListener((ChannelFutureListener) f -> {
                        if (f.isSuccess()) {
                            System.out.println("连接Netty服务端成功: " + getServerInfo());
                        } else {
                            System.out.println("连接失败，进行断线重连: " + getServerInfo());
                            f.channel().eventLoop().schedule(this::doConnect, 1, TimeUnit.SECONDS);
                        }
                    }).sync();
            if (!nettyClientHandler.active.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Local mock channel did not become active");
            }
        } catch (Exception e) {
            log.error(e);
            throw new IllegalStateException("Cannot connect to local mock server", e);
        }
    }

    private String getServerInfo() {
        return String.format("RemoteHost=%s RemotePort=%d", remoteHost, remotePort);
    }

    public void shutdown() {
        closed = true;
        if (nettyClientHandler != null) nettyClientHandler.timer.stop();
        workerGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly();
    }

    static class NettyClientHandler extends ByteToMessageDecoder {

        int size = -1;
        private final NettySocketClient client;
        private final Timer timer = new HashedWheelTimer();
        private final ConcurrentLinkedQueue<TimePromise> messageList = new ConcurrentLinkedQueue<>();
        private volatile ChannelHandlerContext ctx;
        private final CountDownLatch active = new CountDownLatch(1);

        public NettyClientHandler(NettySocketClient client) {
            this.client = client;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            super.channelActive(ctx);
            this.ctx = ctx;
            active.countDown();
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            TimePromise pending = messageList.peek();
            if (pending == null) return;
            if (size < 0) {
                if (pending.msg.toLowerCase().startsWith("dat")) {
                    if (in.readableBytes() < Integer.BYTES) return;
                    size = in.readInt();
                    if (size <= 0 || size > 1024 * 100) {
                        throw new IllegalStateException("Invalid mock frame length: " + size);
                    }
                } else {
                    size = "SMPINF:OK".length();
                }
            }
            if (in.readableBytes() < size) return;
            byte[] message = new byte[size];
            in.readBytes(message);
            size = -1;
            messageList.remove();
            pending.getTimerOut().cancel();
            // Remove the request before completing: listeners may immediately send another.
            pending.getPromise().setSuccess(message);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            final EventLoop eventLoop = ctx.channel().eventLoop();
            eventLoop.schedule(client::doConnect, 1L, TimeUnit.SECONDS);
            super.channelInactive(ctx);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            cause.printStackTrace();
            this.ctx.close();
        }

        public Future<byte[]> sendMessage(String message, long timeOut) {
            if (this.ctx == null)
                throw new IllegalStateException();
            if (message == null || "".equals(message))
                throw new IllegalStateException();
            return sendMessage(message, ctx.executor().newPromise(), timeOut);
        }

        public Future<byte[]> sendMessage(String message, Promise<byte[]> promise, long timeOut) {
            synchronized (this) {
                TimePromise tp = new TimePromise(promise, message, null);
                tp.setTimerOut(timer.newTimeout(new TimeOutTask(tp, messageList), timeOut, TimeUnit.MILLISECONDS));

                if (messageList.offer(tp)) {
                    ctx.writeAndFlush(message);
                } else {
                    tp.timerOut.cancel();
                    promise.setFailure(new BufferOverflowException());
                    return promise;
                }
                return promise;
            }
        }
    }

    static class TimeOutTask implements TimerTask {

        private final TimePromise tp;
        private final ConcurrentLinkedQueue<TimePromise> messageList;

        public TimeOutTask(TimePromise tp, ConcurrentLinkedQueue<TimePromise> messageList) {
            this.tp = tp;
            this.messageList = messageList;
        }

        @Override
        public void run(Timeout timeout) {
            if (!tp.getPromise().isDone()) {
                tp.getPromise().setFailure(new TimeoutException());
                messageList.remove(tp);

            }
        }
    }

    static class TimePromise {

        private final String msg;
        private final Promise<byte[]> promise;
        private Timeout timerOut;

        public TimePromise(Promise<byte[]> promise, String msg, Timeout timerOut) {
            super();
            this.promise = promise;
            this.timerOut = timerOut;
            this.msg = msg;
        }

        public Promise<byte[]> getPromise() {
            return promise;
        }

        public Timeout getTimerOut() {
            return timerOut;
        }

        public void setTimerOut(Timeout timerOut) {
            this.timerOut = timerOut;
        }
    }
}
