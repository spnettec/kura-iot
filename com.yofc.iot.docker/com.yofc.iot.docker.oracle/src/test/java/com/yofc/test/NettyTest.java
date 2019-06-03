package com.yofc.test;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class NettyTest {

    private static final Logger logger = LoggerFactory.getLogger(NettyTest.class);

    @BeforeEach
    @DisplayName("开始")
    public void setUp(Vertx vertx, VertxTestContext context) {
        context.completeNow();
    }

    @AfterEach
    @DisplayName("结束")
    public void tearDown(Vertx vertx, VertxTestContext context) {
        context.completeNow();
    }

    @Test
    @Timeout(value = 100, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Netty测试")
    public void testNetty(Vertx vertx, VertxTestContext context) throws InterruptedException {
        TcpClient client = new TcpClient();
        client.connect("10.211.55.4", 3000);

        client.addHandle(buffer -> {
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
                // context.completeNow();
            } else
                System.out.println("订单: " + buffer.getIntLE(2));
        });

        ByteBuf buf = get10003DataBuf(506, 0, 0, 0, 1, 255, 0, 1, 4, 1001, 28902, 1002, 28902);

        Object receive = client.sendAndReply(buf);
        ByteBuf byteBuf = (ByteBuf) receive;
        int head = byteBuf.getUnsignedShortLE(0);
        logger.debug("返回头: {}", head);
    }

    private ByteBuf get10003DataBuf(int orderNo, int orderStep, int taskType, int productType, int priority,
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
        return buf.getByteBuf();
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