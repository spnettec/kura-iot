package com.yofc.test;

import java.nio.BufferOverflowException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class SocketVerticle extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(SocketVerticle.class);
    private NetSocket socket;
    private Handler<String> handler;
    private String host = "127.0.0.1";
    private int port = 8000;
    private BlockingQueue<Promise<String>> messageList = new ArrayBlockingQueue<>(16);
    private ConcurrentHashMap<String, Promise<String>> messageMap = new ConcurrentHashMap<>(new LinkedHashMap<>() {

        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Promise<String>> eldest) {
            return size() > 16;
        }
    });

    public SocketVerticle() {

    }

    public SocketVerticle(String host) {
        this.host = host;
    }

    public SocketVerticle(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public NetSocket getSocket() {
        return socket;
    }

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        NetClientOptions options = new NetClientOptions();
        options.setTcpKeepAlive(true);
        options.setConnectTimeout(1000);
        options.setReconnectAttempts(10);// 尝试次数
        options.setReconnectInterval(500);// 每一次尝试间隔
        options.setLogActivity(true);
        NetClient netClient = vertx.createNetClient(options);
        logger.info("启动...");
        netClient.connect(this.port, this.host, result -> {
            if (result.failed()) {
                logger.error("连接失败...", result.cause());
                startPromise.fail(result.cause());
                return;
            } else {
                logger.info("连接到服务器:{},端口:{} 成功", this.host, this.port);
                socket = result.result();
                startPromise.complete();
                socket.handler(this::handlerBuffer);
                vertx.setPeriodic(1000, t -> {
                    double random = Math.random();
                    sendAndReply(random + ":CONNECTION?\r\n", handler -> {
                        if (handler.failed()) {
                            log.error("error", handler.cause());
                            return;
                        }
                        String receive = handler.result();

                        System.out.println("发送：" + random + ":CONNECTION?" + "--返回心跳: " + receive.toString());
                    });
                });
            }
        });
    }

    private void handlerBuffer(Buffer buffer) {
        String message = buffer.toString();
        String key = null;
        String[] msgs = message.split(":", 2);
        if (msgs.length > 1) {
            key = msgs[0];
            message = msgs[1];
        }
        if (message != null && (message.startsWith("FEED"))) {
            if (handler != null)
                handler.handle(message);
            return;
        }
        if (key == null || "".equals(key)) {
            if (messageList != null) {
                messageList.poll().complete(message);
            }
        } else {
            if (messageMap != null) {
                Promise<String> promise = messageMap.get(key);
                if (promise != null)
                    promise.complete(message);
                messageMap.remove(key);
            }
        }
    }

    public void addHandle(Handler<String> handler) {
        this.handler = handler;
    }

    public void write(Buffer buffer) {
        socket.write(buffer);
    }

    public void write(String buffer) {
        socket.write(buffer);
    }

    public void sendAndReply(String message, Handler<AsyncResult<String>> handler) {
        sendAndReply(message, 5000, handler);
    }

    public void sendAndReply(String message, long timeOut, Handler<AsyncResult<String>> handler) {
        synchronized (this) {
            String key = null;
            String[] msgs = message.split(":", 2);
            if (msgs.length > 1) {
                key = msgs[0];
                message = msgs[1];
            }
            Promise<String> promise = Promise.promise();
            promise.future().onComplete(handler);
            if (key == null || "".equals(key)) {
                if (messageList == null) {
                    // Connection closed
                    promise.fail(new IllegalStateException());
                } else if (messageList.offer(promise)) {
                    // Connection open and message accepted
                    socket.write(message);
                } else {
                    // Connection open and message rejected
                    promise.fail(new BufferOverflowException());
                    return;
                }
            } else {
                if (messageMap == null) {
                    // Connection closed
                    promise.fail(new IllegalStateException());
                    return;
                } else {
                    messageMap.put(key, promise);
                    // Connection open and message accepted
                    socket.write(key + ":" + message);
                }
            }
            vertx.setTimer(timeOut, new TimeOutHandle(key, promise, messageList, messageMap));
        }
    }

    class TimeOutHandle implements Handler<Long> {

        public TimeOutHandle(String key, Promise<String> promise, BlockingQueue<Promise<String>> messageList,
                ConcurrentHashMap<String, Promise<String>> messageMap) {
            this.key = key;
            this.promise = promise;
            this.messageList = messageList;
            this.messageMap = messageMap;

        }

        private final Promise<String> promise;
        private final String key;
        private final BlockingQueue<Promise<String>> messageList;
        private final ConcurrentHashMap<String, Promise<String>> messageMap;

        @Override
        public void handle(Long event) {
            if (!promise.future().isComplete()) {
                if (key == null || "".equals(key))
                    messageList.remove(promise);
                else
                    messageMap.remove(key);
            }
        }

    }
}
