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
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class SocketVerticleTest {

    private static final Logger logger = LoggerFactory.getLogger(SocketVerticleTest.class);
    private SocketVerticle agv;

    @BeforeEach
    @DisplayName("开始")
    public void setUp(Vertx vertx, VertxTestContext context) {
        agv = new SocketVerticle("10.211.55.3", 8000);
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
    @Timeout(value = 10000, timeUnit = TimeUnit.SECONDS)
    @DisplayName("AGV协议测试")
    public void testMyApplication(Vertx vertx, VertxTestContext context) {

        agv.addHandle(buffer -> {
            String result = buffer.toString();
            if (result != null && result.startsWith("FEED")) {
                System.out.println("返回监听: " + buffer.toString());
            }
        });
        agv.sendAndReply("GEO GO\r\n", 2000, handler -> {
            if (handler.failed()) {
                logger.error("error", handler.cause());
                return;
            }
            Buffer receive = handler.result();

            System.out.println("返回应答: " + receive.toString());
        });
    }

}