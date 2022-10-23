package com.yofc.test;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.parsetools.RecordParser;
import lombok.extern.log4j.Log4j2;

import java.nio.BufferOverflowException;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeoutException;

@Log4j2
public class VerxSocketClient extends AbstractVerticle {

    private final Queue<TimePromise> messageList = new ArrayBlockingQueue<>(16);
    NetClientOptions options;
    private NetClient netClient;
    private NetSocket socket;
    private Handler<Buffer> handler;
    RecordParser parser = RecordParser.newFixed(4);
    RecordParser textParser = RecordParser.newDelimited("\r\n");
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
        parser.setOutput(new Handler<>() {

            int size = -1;

            @Override
            public void handle(Buffer buffer) {
                TimePromise tp = messageList.peek();
                if (tp == null) {
                    buffer.getBytes();
                    return;
                }
                if (size == -1) {
                    size = buffer.getInt(0);
                    if(size<=0)
                    {
                        return;
                    }
                    if (size > 1024 * 100) {
                        size = 1024 * 100;
                    }
                    if (size > 100) {
                        size = size * 2;
                    }
                    parser.fixedSizeMode(size);
                } else {

                    if (!tp.getPromise().future().isComplete()) {
                        tp.getPromise().complete(Buffer.buffer(buffer.getBytes()));
                    }
                    if (tp.timer > 0) {
                        vertx.cancelTimer(tp.timer);
                    }
                    parser.fixedSizeMode(4);
                    size = -1;
                    messageList.remove();
                }
            }

        });
        textParser.setOutput(buffer -> {
            TimePromise tp = messageList.poll();
            if (tp == null) {
                buffer.getBytes();
                return;
            }
            tp.getPromise().complete(Buffer.buffer(buffer.getBytes()));
            if (tp.timer > 0) {
                vertx.cancelTimer(tp.timer);
            }
        });
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
        TimePromise tp = messageList.peek();
        if (tp == null) {
            buffer.getBytes();
            if (handler != null) {
                handler.handle(Buffer.buffer(buffer.getBytes()));
            }
            return;
        }
        String message = tp.msg;
        if(message.toLowerCase().startsWith("dat"))
        {
            parser.handle(buffer);
        } else {
            textParser.handle(buffer);
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

        Promise<Buffer> promise = Promise.promise();
        promise.future().onComplete(handler);
        TimePromise tp = new TimePromise(promise, -1L, message);
        long timer = vertx.setTimer(timeOut, new TimeOutTask(tp));
        tp.setTimer(timer);

        if (messageList.offer(tp)) {
            socket.write(message).onFailure(ex -> messageList.remove(tp));
        } else {
            vertx.cancelTimer(timer);
            promise.fail(new BufferOverflowException());
        }
    }

    static class TimeOutTask implements Handler<Long> {

        private final TimePromise tp;

        public TimeOutTask(TimePromise tp) {
            this.tp = tp;
        }

        @Override
        public void handle(Long event) {
            if (!tp.getPromise().future().isComplete()) {
                tp.getPromise().fail(new TimeoutException());
                tp.setTimer(-1L);
            }
        }
    }

    static class TimePromise {

        private final String msg;
        private final Promise<Buffer> promise;
        private Long timer;

        public TimePromise(Promise<Buffer> promise, Long timer, String msg) {
            super();
            this.promise = promise;
            this.timer = timer;
            this.msg = msg;
        }

        public Promise<Buffer> getPromise() {
            return promise;
        }

        public void setTimer(Long timer) {
            this.timer = timer;
        }

    }
}
