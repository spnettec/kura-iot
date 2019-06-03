package com.yofc.test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;

public class AgvVerticle extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(AgvVerticle.class);
    private NetSocket socket;
    private Handler<Buffer> handler;
    private String host = "127.0.0.1";
    private int port = 3000;

    public AgvVerticle() {

    }

    public AgvVerticle(String host) {
        this.host = host;
    }

    public AgvVerticle(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public NetSocket getSocket() {
        return socket;
    }

    @Override
    public void start(Future<Void> startFuture) throws Exception {
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
                startFuture.fail(result.cause());
                return;
            } else {
                logger.info("连接到服务器:{},端口:{} 成功", this.host, this.port);
                socket = result.result();
                startFuture.complete();
                socket.handler(this::handlerBuffer);
            }
        });
    }

    private void handlerBuffer(Buffer buffer) {
        int head = buffer.getUnsignedShortLE(0);
        if (head == 10011) {
            write(buffer);
        }
        if (handler != null)
            handler.handle(buffer);
    }

    public void addHandle(Handler<Buffer> handler) {
        this.handler = handler;
    }

    public void write(Buffer buffer) {
        socket.write(buffer);
    }

    public Buffer get10003DataBuf(int orderNo, int orderStep, int taskType, int productType, int priority,
            int vehicleType, int vehicleNo, int nextTag, int paraNum, int paraPosRecv, int paraOpRecv, int paraPosSend,
            int paraOpSend) {
        Buffer buf = Buffer.buffer();
        buf.appendUnsignedShortLE(10003);
        buf.appendUnsignedIntLE(orderNo);
        buf.appendUnsignedShortLE(orderStep);
        buf.appendUnsignedShortLE(taskType);
        buf.appendUnsignedShortLE(productType);
        buf.appendUnsignedShortLE(priority);
        buf.appendUnsignedShortLE(vehicleType);
        buf.appendUnsignedShortLE(vehicleNo);
        buf.appendUnsignedShortLE(nextTag);
        buf.appendUnsignedShortLE(paraNum);
        buf.appendUnsignedShortLE(paraPosRecv);
        buf.appendUnsignedShortLE(paraOpRecv);
        buf.appendUnsignedShortLE(paraPosSend);
        buf.appendUnsignedShortLE(paraOpSend);
        buf.appendUnsignedShortLE(checkSum(buf));
        return buf;
    }

    private static int checkSum(Buffer buf) {
        int sum = 0;
        if (buf != null) {
            for (int i = 0; i < buf.length(); i++) {
                sum += buf.getByte(i);
            }
        }
        return sum & 0xff;
    }
}
