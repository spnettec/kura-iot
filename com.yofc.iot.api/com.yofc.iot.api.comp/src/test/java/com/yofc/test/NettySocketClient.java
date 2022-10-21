package com.yofc.test;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.TimerTask;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import lombok.extern.log4j.Log4j2;

import java.net.InetSocketAddress;
import java.nio.BufferOverflowException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

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

    /** 发送消息 */
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
            public void initChannel(SocketChannel ch) throws Exception {
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
                        protected void initChannel(SocketChannel socketChannel) throws Exception {

                            socketChannel.pipeline().addLast(nettyClientHandler);
                        }
                    }).connect(new InetSocketAddress(remoteHost, remotePort)).addListener(new ChannelFutureListener() {

                        public void operationComplete(ChannelFuture f) throws Exception {
                            if (f.isSuccess()) {
                                System.out.println("连接Netty服务端成功: " + getServerInfo());
                            } else {
                                System.out.println("连接失败，进行断线重连: " + getServerInfo());
                                f.channel().eventLoop().schedule(() -> doConnect(), 1, TimeUnit.SECONDS);
                            }
                        }
                    }).sync();
        } catch (Exception e) {
            log.error(e);
            doConnect();
        }
    }

    private String getServerInfo() {
        return String.format("RemoteHost=%s RemotePort=%d", remoteHost, remotePort);
    }

    public void shutdown() {
        closed = true;
        workerGroup.shutdownGracefully();
    }

    static class NettyClientHandler extends SimpleChannelInboundHandler<byte[]> {

        private final NettySocketClient client;
        private static final Timer timer = new HashedWheelTimer();
        private final ConcurrentLinkedQueue<TimePromise> messageList = new ConcurrentLinkedQueue<>();
        private final ConcurrentHashMap<String, TimePromise> messageMap = new ConcurrentHashMap<>(
                new LinkedHashMap<>() {

                    private static final long serialVersionUID = 1L;

                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, TimePromise> eldest) {
                        return size() > 16;
                    }
                });
        private ChannelHandlerContext ctx;

        public NettyClientHandler(NettySocketClient client) {
            this.client = client;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            super.channelActive(ctx);
            this.ctx = ctx;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, byte[] message) {
            synchronized (this) {
                    TimePromise tp = messageList.poll();
                    if (tp != null) {
                        tp.getPromise().setSuccess(message);
                        tp.getTimerOut().cancel();
                    }
            }
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
                String key = null;
                String[] msgs = message.split(":", 2);
                if (msgs.length > 1) {
                    key = msgs[0];
                    message = msgs[1];
                }
                if (key == null || "".equals(key)) {

                    TimePromise tp = new TimePromise(promise, null);
                    tp.setTimerOut(timer.newTimeout(new TimeOutTask(key, tp, messageList, messageMap), timeOut,
                            TimeUnit.SECONDS));

                    if (messageList.offer(tp)) {
                        ctx.writeAndFlush(message.getBytes(StandardCharsets.UTF_8));
                    } else {
                        timer.stop();
                        promise.setFailure(new BufferOverflowException());
                        return promise;
                    }
                } else {
                    TimePromise tp = new TimePromise(promise, null);
                    tp.setTimerOut(timer.newTimeout(new TimeOutTask(key, tp, messageList, messageMap), timeOut,
                            TimeUnit.SECONDS));
                    messageMap.put(key, tp);
                    ctx.writeAndFlush((key + ":" + message).getBytes(StandardCharsets.UTF_8));
                }

                return promise;
            }
        }
    }

    static class TimeOutTask implements TimerTask {

        private final TimePromise tp;
        private final String key;
        private final ConcurrentLinkedQueue<TimePromise> messageList;
        private final ConcurrentHashMap<String, TimePromise> messageMap;

        public TimeOutTask(String key, TimePromise tp, ConcurrentLinkedQueue<TimePromise> messageList,
                ConcurrentHashMap<String, TimePromise> messageMap) {
            this.key = key;
            this.tp = tp;
            this.messageList = messageList;
            this.messageMap = messageMap;
        }

        @Override
        public void run(Timeout timeout) {
            if (!tp.getPromise().isDone()) {
                tp.getPromise().setFailure(new TimeoutException());
                if (key == null || "".equals(key))
                    messageList.remove(tp);
                else
                    messageMap.remove(key);
            }
        }
    }

    static class TimePromise {

        private final Promise<byte[]> promise;
        private Timeout timerOut;

        public TimePromise(Promise<byte[]> promise, Timeout timerOut) {
            super();
            this.promise = promise;
            this.timerOut = timerOut;
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
