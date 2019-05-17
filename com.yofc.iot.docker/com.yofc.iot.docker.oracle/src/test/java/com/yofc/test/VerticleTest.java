package com.yofc.test;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class VerticleTest {

    private static final Logger logger = LoggerFactory.getLogger(VerticleTest.class);
    private Vertx vertx;
    private NetSocket socket;

    @Before
    public void setUp(TestContext context) {
        vertx = Vertx.vertx();
    }

    @After
    public void tearDown(TestContext context) {
        vertx.close(context.asyncAssertSuccess());
    }

    @Test
    public void testMyApplication(TestContext context) {
        Async async = context.async();
        NetClientOptions options = new NetClientOptions();
        options.setTcpKeepAlive(true);
        options.setConnectTimeout(1000);
        options.setReconnectAttempts(10);// 尝试次数
        options.setReconnectInterval(500);// 每一次尝试间隔
        options.setLogActivity(true);
        NetClient tcpClient = vertx.createNetClient(options);
        logger.info("启动...");
        tcpClient.connect(3000, "10.211.55.4", result -> {

            if (result.failed()) {
                logger.error("连接失败...",result.cause());
                async.complete();
                return;
            }
            socket = result.result();
            socket.handler(buffer -> {
                int head = buffer.getUnsignedShortLE(0);
                System.out.print("头: " + head + "  ");
                if (head == 10011) {
                    System.out.print("是否回复: " + buffer.getUnsignedShortLE(2) + "   ");
                    int nums = buffer.getUnsignedShortLE(4);
                    if (nums > 0) {
                        System.out.print("数据个数: " + nums + "   ");
                        for (int i = 1; i <= nums; i++) {
                            System.out.print("第" + i + "个数: " + buffer.getUnsignedShortLE(4 + i * 2) + "   ");
                        }
                    }
                    System.out.println();
                    socket.write(buffer);
                    async.complete();
                } else
                    System.out.println("订单: " + buffer.getIntLE(2));
                // async.complete();
            });
            short header = 10003;
            int orderNo = 506;
            short orderStep = 0;
            short taskType = 0;
            short productType = 0;
            short priority = 1;
            short vehicleType = 255;
            short vehicleNo = 0;
            short nextTag = 1;
            short paraNum = 4;
            short paraPosRecv = 1001;
            short paraOpRecv = 28902;
            short paraPosSend = 1002;
            short paraOpSend = 28902;

            Buffer buf = Buffer.buffer();
            buf.appendUnsignedShortLE(header);
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
            buf.appendUnsignedShortLE(checkSum(buf.getBytes()));
            socket.write(buf);
        });
        async.awaitSuccess();
    }

    public static byte checkSum(byte[] src) {
        int sum = 0;
        if (src != null) {
            for (int i = 0; i < src.length; i++) {
                sum += src[i];
            }
        }
        return (byte) (sum & 0xff);
    }
}