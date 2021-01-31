package com.yofc.test;

import java.net.InetSocketAddress;
import java.nio.BufferOverflowException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
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

@Log4j2
public class NettySocketClient {

    private EventLoopGroup workerGroup = new NioEventLoopGroup();
    private volatile Bootstrap bootstrap;
    private volatile boolean closed = false;
    private final String remoteHost;
    private NettyClientHandler nettyClientHandler;
    private final int remotePort;
    private Consumer<String> consumer;

    public Consumer<String> getConsumer() {
        return consumer;
    }

    public NettySocketClient(String remoteHost, int remotePort) {
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
    }

    /**
     * 发送消息
     */
    public Future<String> sendMessage(String msg) {
        return nettyClientHandler.sendMessage(msg);
    }

    public void addLister(Consumer<String> consumer) {
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
                        ctx.channel().eventLoop().schedule(() -> doConnect(), 1, TimeUnit.SECONDS);
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
                            socketChannel.pipeline().addLast("decoder", new StringDecoder());
                            socketChannel.pipeline().addLast("encoder", new StringEncoder());
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
        } catch (InterruptedException e) {
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

    class NettyClientHandler extends SimpleChannelInboundHandler<String> {

        private NettySocketClient client;

        public NettyClientHandler(NettySocketClient client) {
            this.client = client;
        }

        private ChannelHandlerContext ctx;

        private BlockingQueue<TimePromise> messageList = new ArrayBlockingQueue<>(16);
        private ConcurrentHashMap<String, TimePromise> messageMap = new ConcurrentHashMap<>(new LinkedHashMap<>() {

            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<String, TimePromise> eldest) {
                return size() > 16;
            }
        });

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            super.channelActive(ctx);
            this.ctx = ctx;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, String message) throws Exception {
            synchronized (this) {

                String key = null;
                String[] msgs = message.split(":", 2);
                if (msgs.length > 1) {
                    key = msgs[0];
                    message = msgs[1];
                }
                if (message != null && (message.startsWith("FEED"))) {
                    if (this.client.getConsumer() != null)
                        this.client.getConsumer().accept(message);
                    return;
                }
                if (key == null || "".equals(key)) {
                    if (messageList != null) {
                        TimePromise tp = messageList.poll();
                        tp.getPromise().setSuccess(message);
                        tp.getTimer().stop();
                    }
                } else {
                    if (messageMap != null) {
                        TimePromise tp = messageMap.get(key);
                        if (tp != null) {
                            tp.getPromise().setSuccess(message);
                            tp.getTimer().stop();
                        }
                        messageMap.remove(key);
                    }
                }
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            final EventLoop eventLoop = ctx.channel().eventLoop();
            eventLoop.schedule(new Runnable() {

                @Override
                public void run() {
                    client.doConnect();
                }
            }, 1L, TimeUnit.SECONDS);
            super.channelInactive(ctx);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            cause.printStackTrace();
            this.ctx.close();
        }

        public Future<String> sendMessage(String message) {
            if (this.ctx == null)
                throw new IllegalStateException();
            if (message == null || "".equals(message))
                throw new IllegalStateException();
            return sendMessage(message, ctx.executor().newPromise());
        }

        public Future<String> sendMessage(String message, Promise<String> promise) {
            synchronized (this) {
                String key = null;
                String[] msgs = message.split(":", 2);
                if (msgs.length > 1) {
                    key = msgs[0];
                    message = msgs[1];
                }
                if (key == null || "".equals(key)) {
                    if (messageList == null) {
                        promise.setFailure(new IllegalStateException());
                        return promise;
                    }
                    Timer timer = new HashedWheelTimer();
                    TimePromise tp = new TimePromise(promise, timer);
                    timer.newTimeout(new TimeOutTask(key, tp, messageList, messageMap), 10, TimeUnit.SECONDS);

                    if (messageList.offer(tp)) {
                        ctx.writeAndFlush(message);
                    } else {
                        timer.stop();
                        promise.setFailure(new BufferOverflowException());
                        return promise;
                    }
                } else {
                    if (messageMap == null) {
                        // Connection closed
                        promise.setFailure(new IllegalStateException());
                        return promise;
                    } else {
                        Timer timer = new HashedWheelTimer();
                        TimePromise tp = new TimePromise(promise, timer);
                        timer.newTimeout(new TimeOutTask(key, tp, messageList, messageMap), 10, TimeUnit.SECONDS);
                        messageMap.put(key, tp);
                        // Connection open and message accepted
                        ctx.writeAndFlush(key + ":" + message);
                    }
                }

                return promise;
            }
        }
    }

    class TimeOutTask implements TimerTask {

        public TimeOutTask(String key, TimePromise tp, BlockingQueue<TimePromise> messageList,
                ConcurrentHashMap<String, TimePromise> messageMap) {
            this.key = key;
            this.tp = tp;
            this.messageList = messageList;
            this.messageMap = messageMap;

        }

        private final TimePromise tp;
        private final String key;
        private final BlockingQueue<TimePromise> messageList;
        private final ConcurrentHashMap<String, TimePromise> messageMap;

        @Override
        public void run(Timeout timeout) throws Exception {
            if (!tp.getPromise().isDone()) {
                tp.getPromise().setFailure(new TimeoutException());
                if (key == null || "".equals(key))
                    messageList.remove(tp);
                else
                    messageMap.remove(key);
            }
        }

    }

    class TimePromise {

        private final Promise<String> promise;

        public Promise<String> getPromise() {
            return promise;
        }

        public Timer getTimer() {
            return timer;
        }

        private final Timer timer;

        public TimePromise(Promise<String> promise, Timer timer) {
            super();
            this.promise = promise;
            this.timer = timer;
        }
    }
}
