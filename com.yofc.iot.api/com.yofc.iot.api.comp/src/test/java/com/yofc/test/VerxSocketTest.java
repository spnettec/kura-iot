package com.yofc.test;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.log4j.Log4j2;

@Log4j2
@ExtendWith(VertxExtension.class)
public class VerxSocketTest {

    private VerxSocketClient client;

    @BeforeEach
    @DisplayName("开始")
    public void setUp(Vertx vertx, VertxTestContext context) {
        client = new VerxSocketClient("10.211.55.3", 8000);
        vertx.deployVerticle(client, context.succeeding(id -> {
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
    @DisplayName("Vertx协议测试")
    public void testMyApplication(Vertx vertx, VertxTestContext context) {

        client.addHandle(buffer -> {
            System.out.println("返回监听: " + buffer);

        });
        vertx.setPeriodic(1000, t -> {
            double random = Math.random();
            client.sendAndReply(random + ":CONNECTION?\r\n", handler -> {
                if (handler.failed()) {
                    log.error("error", handler.cause());
                    return;
                }
                String receive = handler.result();

                System.out.println("发送：" + random + ":CONNECTION?" + "--返回心跳: " + receive.toString());
            });
        });
        vertx.setPeriodic(2500, l -> {
            double random = Math.random();
            client.sendAndReply(random + ":GEO GO\r\n", handler -> {
                if (handler.failed()) {
                    log.error("error", handler.cause());
                    return;
                }
                String receive = handler.result();

                System.out.println("发送：" + random + ":GEO GO" + "--返回应答: " + receive.toString());
            });
        });
    }

}