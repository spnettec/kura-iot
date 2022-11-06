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
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

@Log4j2
public class VerxSocketClient extends AbstractVerticle {

    private final Queue<TimePromise> messageList = new ArrayBlockingQueue<>(16);
    NetClientOptions options;
    private NetClient netClient;
    private NetSocket socket;
    private Handler<Buffer> handler;
    protected volatile AtomicReference<TimePromise> runningTp = new AtomicReference<>(null);
    MixRecordParser parser = MixRecordParser.newMixed("\r\n", 4);
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
        parser.setOutput(buffer -> {
            TimePromise tp = runningTp.get();
            if (parser.isDelimitedMode()) {
                tp.getPromise().complete(buffer);
                if (tp.timer != -1L) {
                    vertx.cancelTimer(tp.timer);
                }
                return;
            }
            if (parser.getSize() == 4) {
                int size = buffer.getInt(0);
                if (size < 0) {
                    return;
                }
                if (tp.msg.toLowerCase().startsWith("dat")) {
                    size = size * 2;
                }
                if (size == 0) {
                    if (!tp.getPromise().future().isComplete()) {
                        tp.getPromise().complete();
                    }
                    if (tp.timer > 0) {
                        vertx.cancelTimer(tp.timer);
                    }
                    parser.fixedSizeMode(4);
                } else {
                    parser.fixedSizeMode(size);
                }
            } else {
                tp.getPromise().complete(buffer);
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
        TimePromise tp = runningTp.get();
        if (tp == null) {
            if (handler != null) {
                handler.handle(buffer);
            }
            return;
        }
        parser.handle(buffer);
    }

    public void addHandle(Handler<Buffer> handler) {
        this.handler = handler;
    }

    public void sendAndReply(String message, Handler<AsyncResult<Buffer>> handler) {
        sendAndReply(message, 5000, handler);
    }

    private void setParserMode(String message) {
        if (message.toLowerCase().startsWith("dat") || message.toLowerCase().startsWith("getfile")) {
            if (parser.isDelimitedMode()) {
                parser.fixedSizeMode(4);
            }
        } else {
            parser.switchDelimitedMode();
        }
    }

    public synchronized void sendAndReply(String message, long timeOut, Handler<AsyncResult<Buffer>> handler) {
        Promise<Buffer> promise = Promise.promise();
        promise.future().onComplete(handler);
        if (message.toLowerCase().startsWith("rst")) {
            socket.write(message).onSuccess(v -> promise.complete()).onFailure(promise::fail);
            return;
        }
        TimePromise tp = new TimePromise(promise, timeOut, message);
        if (!messageList.offer(tp)) {
            promise.fail(new BufferOverflowException());
        }
        runSend();
    }

    private synchronized void runSend() {
        if (runningTp.get() != null || messageList.isEmpty()) {
            return;
        }
        TimePromise tp = messageList.poll();
        if (tp != null) {
            runningTp.set(tp);
            String message = tp.msg;
            setParserMode(message);
            socket.write(message).onSuccess(v -> {
                long timer = vertx.setTimer(tp.timeOut, t -> {
                    if (!tp.getPromise().future().isComplete()) {
                        tp.getPromise().fail(new TimeoutException());
                        tp.setTimer(-1L);
                    }
                });
                tp.setTimer(timer);
            }).onFailure(tp.promise::fail);
            tp.promise.future().onComplete(f -> {
                runningTp.set(null);
                runSend();
            });

        }
    }

    static class TimePromise {

        private final String msg;
        private final Promise<Buffer> promise;
        private Long timer;
        private final Long timeOut;

        public TimePromise(Promise<Buffer> promise, Long timeOut, String msg) {
            super();
            this.promise = promise;
            this.timeOut = timeOut;
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
