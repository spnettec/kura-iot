package com.yofc.test;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import lombok.extern.log4j.Log4j2;

import java.nio.BufferOverflowException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeoutException;

@Log4j2
public class VerxSocketClient extends AbstractVerticle {

    private final ConcurrentLinkedQueue<TimePromise> messageList = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<String, TimePromise> messageMap = new ConcurrentHashMap<>(new LinkedHashMap<>() {

        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, TimePromise> eldest) {
            return size() > 16;
        }
    });
    NetClientOptions options;
    private NetClient netClient;
    private NetSocket socket;
    private Handler<Buffer> handler;
    private String host = "127.0.0.1";
    private int port = 8000;
    private volatile boolean closed = false;

    public VerxSocketClient() {
    }

    public VerxSocketClient(String host) {
        this.host = host;
    }

    public VerxSocketClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public NetSocket getSocket() {
        return socket;
    }

    public void shutdown() {
        closed = true;
        socket.close();
        netClient.close();
    }

    public void init() {
        init(null);
    }

    public void init(Promise<Void> startPromise) {
        closed = false;
        options = new NetClientOptions();
        options.setTcpKeepAlive(true);
        options.setConnectTimeout(1000);
        options.setReconnectAttempts(10); // 尝试次数
        options.setReconnectInterval(1); // 每一次尝试间隔
        options.setLogActivity(true);
        options.setReuseAddress(true);
        log.info("启动...");
        doConnect(startPromise);
    }

    private void doConnect(Promise<Void> startPromise) {
        if (closed) {
            return;
        }
        if (netClient != null) {
            netClient.close();
            netClient = null;
        }
        netClient = vertx.createNetClient(options);
        netClient.connect(this.port, this.host, handleConnection(startPromise, this));
    }

    @Override
    public void start(Promise<Void> startPromise) {
        init(startPromise);
    }

    private Handler<AsyncResult<NetSocket>> handleConnection(Promise<Void> startPromise, VerxSocketClient client) {
        return (result) -> {
            if (result.failed()) {
                log.error("连接失败...重新尝试");
                if (socket != null) {
                    socket.handler(null);
                    socket.exceptionHandler(null);
                    socket.closeHandler(null);
                }
                client.doConnect(startPromise);

            } else {
                log.info("连接到服务器:{},端口:{} 成功", this.host, this.port);
                socket = result.result();

                socket.closeHandler(h -> {
                    log.info("Closed");
                    socket.handler(null);
                    socket.exceptionHandler(null);
                    socket.closeHandler(null);
                    client.doConnect(startPromise);
                });
                socket.exceptionHandler(log::error);
                if (startPromise != null && !startPromise.future().isComplete())
                    startPromise.complete();
                socket.handler(this::handlerBuffer);
            }
        };
    }

    private void handlerBuffer(Buffer buffer) {
        TimePromise tp = messageList.poll();
        if (tp != null) {
            tp.getPromise().complete(buffer);
            vertx.cancelTimer(tp.getTimer());
        } else if(this.handler!=null){
            this.handler.handle(buffer);
        }

    }

    public void addHandle(Handler<Buffer> handler) {
        this.handler = handler;
    }

    public void write(Buffer buffer) {
        socket.write(buffer);
    }

    public void write(String buffer) {
        socket.write(buffer);
    }

    public void sendAndReply(String message, Handler<AsyncResult<Buffer>> handler) {
        sendAndReply(message, 5000, handler);
    }

    public void sendAndReply(String message, long timeOut, Handler<AsyncResult<Buffer>> handler) {
        synchronized (this) {
            String key = null;
            String[] msgs = message.split(":", 2);
            if (msgs.length > 1) {
                key = msgs[0];
                message = msgs[1];
            }
            Promise<Buffer> promise = Promise.promise();
            promise.future().onComplete(handler);
            TimePromise tp = new TimePromise(promise, 0L);
            if (key == null || "".equals(key)) {

                long timer = vertx.setTimer(timeOut, new TimeOutTask(key, tp, messageList, messageMap));
                tp.setTimer(timer);

                if (messageList.offer(tp)) {
                    socket.write(message);
                } else {
                    vertx.cancelTimer(timer);
                    promise.fail(new BufferOverflowException());
                }
            } else {
                Long timer = vertx.setTimer(timeOut, new TimeOutTask(key, tp, messageList, messageMap));
                tp.setTimer(timer);
                messageMap.put(key, tp);
                socket.write(key + ":" + message);
            }
        }
    }

    static class TimeOutTask implements Handler<Long> {

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
        public void handle(Long event) {
            if (!tp.getPromise().future().isComplete()) {
                tp.getPromise().fail(new TimeoutException());
                if (key == null || "".equals(key)) {
                    messageList.remove(tp);
                } else {
                    messageMap.remove(key);
                }
            }
        }
    }

    static class TimePromise {

        private final Promise<Buffer> promise;
        private Long timer;

        public TimePromise(Promise<Buffer> promise, Long timer) {
            super();
            this.promise = promise;
            this.timer = timer;
        }

        public Promise<Buffer> getPromise() {
            return promise;
        }

        public Long getTimer() {
            return timer;
        }

        public void setTimer(Long timer) {
            this.timer = timer;
        }
    }
}
