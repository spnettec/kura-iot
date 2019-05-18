package com.yofc.test;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Vertx;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class VerticleTest {

    private static final Logger logger = LoggerFactory.getLogger(VerticleTest.class);
    private AgvVerticle agv;

    @BeforeEach
    @DisplayName("开始")
    public void setUp(Vertx vertx, VertxTestContext context) {
        agv = new AgvVerticle("10.211.55.4");
        vertx.deployVerticle(agv, context.succeeding(id -> {
            context.completeNow();
        }));
    }

    @AfterEach
    @DisplayName("结束")
    public void tearDown(Vertx vertx, VertxTestContext context) {
        context.completeNow();
    }

    @Test
    @Timeout(value = 100, timeUnit = TimeUnit.SECONDS)
    @DisplayName("AGV协议测试")
    public void testMyApplication(Vertx vertx, VertxTestContext context) {

        agv.addHandle(buffer -> {
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
                agv.sendData(buffer);
                // context.completeNow();
            } else
                System.out.println("订单: " + buffer.getIntLE(2));
        });
        agv.send10003Data(506, 0, 0, 0, 1, 255, 0, 1, 4, 1001, 28902, 1002, 28902);
    }

}