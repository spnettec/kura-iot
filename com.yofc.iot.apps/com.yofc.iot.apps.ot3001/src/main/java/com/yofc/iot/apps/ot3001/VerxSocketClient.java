package com.yofc.iot.apps.ot3001;

import java.nio.BufferOverflowException;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;

public class VerxSocketClient {

    private static final Logger log = LoggerFactory.getLogger(VerxSocketClient.class);

    private final Queue<TimePromise> messageList = new ArrayBlockingQueue<>(16);
    NetClientOptions options = new NetClientOptions().setTcpKeepAlive(true).setConnectTimeout(1000)
            .setReconnectAttempts(-1).setReconnectInterval(1000).setLogActivity(false).setReuseAddress(true);
    private Future<NetSocket> connectTask;
    private NetClient netClient;
    Vertx vertx;
    private Handler<Buffer> handler;
    protected AtomicReference<TimePromise> runningTp = new AtomicReference<>(null);
    MixRecordParser parser = MixRecordParser.newMixed("\r\n", 4);
    private String host = "127.0.0.1";
    private int port = 8000;

    public VerxSocketClient() {
    }

    public VerxSocketClient(String host) {
        this.host = host;
    }

    public VerxSocketClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public VerxSocketClient reconnectAttempts(int attempts) {
        this.options.setReconnectAttempts(attempts);
        return this;
    }

    public VerxSocketClient vertx(Vertx vertx) {
        this.vertx = vertx;
        return this;
    }

    public VerxSocketClient reconnectInterval(long interval) {
        this.options.setReconnectInterval(interval);
        return this;
    }

    public Future<NetSocket> connect() {
        connectTask = doConnect();
        return connectTask;
    }

    private synchronized Future<NetSocket> doConnect() {
        if (this.connectTask != null) {
            if (!this.connectTask.isComplete()) {
                return this.connectTask;
            } else {
                Promise<NetSocket> connectPromise = Promise.promise();
                if (this.connectTask.failed()) {
                    connectPromise.fail(this.connectTask.cause());
                } else {
                    connectPromise.complete(connectTask.result());
                }
                return connectPromise.future();
            }
        }
        Promise<NetSocket> connectPromise = Promise.promise();

        if (netClient != null) {
            netClient.close();
            netClient = null;
        }
        if (this.vertx == null) {
            this.vertx = Vertx.vertx();
        }
        netClient = vertx.createNetClient(options);
        netClient.connect(this.port, this.host, handleConnection(connectPromise));
        this.connectTask = connectPromise.future();
        return this.connectTask;
    }

    Handler<Buffer> output = buffer -> {
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
    };

    private synchronized void closeSocketClient() {
        if (this.connectTask != null) {
            if (connectTask.isComplete()) {
                connectTask.result().close();
            }
            this.connectTask = null;
        }
        if (netClient != null) {
            netClient.close();
        }
        messageList.clear();
    }

    private Handler<AsyncResult<NetSocket>> handleConnection(Promise<NetSocket> connectPromise) {
        return result -> {
            if (result.failed()) {
                connectPromise.fail(result.cause());
                VerxSocketClient.this.closeSocketClient();
                if (VerxSocketClient.this.options.getReconnectAttempts() > 0) {
                    log.info("连接到服务器:{},端口:{} 失败次数超过:{}次，服务停止，请重新启动", VerxSocketClient.this.host,
                            VerxSocketClient.this.port, VerxSocketClient.this.options.getReconnectAttempts());
                }
            } else {
                NetSocket netSocket = result.result();
                connectPromise.complete(netSocket);
                log.info("连接到服务器:{},端口:{} 成功", VerxSocketClient.this.host, VerxSocketClient.this.port);
                netSocket.closeHandler(h -> {
                    log.info("连接到服务器:{},端口:{} 连接丢失", VerxSocketClient.this.host, VerxSocketClient.this.port);
                    VerxSocketClient.this.closeSocketClient();
                });
                netSocket.exceptionHandler(e -> {
                    log.info("连接到服务器:{},端口:{} 连接错误", VerxSocketClient.this.host, VerxSocketClient.this.port, e);
                    VerxSocketClient.this.closeSocketClient();
                });

                netSocket.handler(this::handlerBuffer);
                parser.setOutput(output);
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
        this.doConnect().onComplete(socketAsyncResult -> {
            Promise<Buffer> promise = Promise.promise();
            promise.future().onComplete(handler);
            if (socketAsyncResult.failed()) {
                promise.fail(socketAsyncResult.cause());
                return;
            }
            if (message.toLowerCase().startsWith("rst")) {
                socketAsyncResult.result().write(message).onSuccess(v -> promise.complete()).onFailure(promise::fail);
                return;
            }
            TimePromise tp = new TimePromise(promise, timeOut, message);
            if (!messageList.offer(tp)) {
                promise.fail(new BufferOverflowException());
            }
            runSend();
        });
    }

    private synchronized void runSend() {
        if (runningTp.get() != null || messageList.isEmpty()) {
            return;
        }
        this.doConnect().onComplete(socketAsyncResult -> {
            TimePromise tp = messageList.poll();
            if (tp != null) {
                runningTp.set(tp);
                String message = tp.msg;
                setParserMode(message);

                socketAsyncResult.result().write(message).onSuccess(v -> {
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
        });
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
