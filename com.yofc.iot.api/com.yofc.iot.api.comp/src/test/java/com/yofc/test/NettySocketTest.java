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
class NettySocketTest {

    private static NettySocketClient client;

    @BeforeEach
    @DisplayName("开始")
    public void setUp(Vertx vertx, VertxTestContext context) {
        client = new NettySocketClient("10.211.55.3", 8000);
        client.init();
        context.completeNow();

    }

    @AfterEach
    @DisplayName("结束")
    public void tearDown(Vertx vertx, VertxTestContext context) {
        client.shutdown();
        context.completeNow();
    }

    @Test
    @Timeout(value = 10000, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Netty测试")
    public void testMyApplication(Vertx vertx, VertxTestContext context) {

        client.addLister(msg -> {
            System.out.println("返回监听: " + msg);
        });
        vertx.setPeriodic(1000, l -> {
            double random = Math.random();
            client.sendMessage(random + ":CONNECTION?\r\n").addListener(handler -> {
                if (!handler.isSuccess()) {
                    log.error("error", handler.cause());
                    return;
                }
                String receive = (String) handler.get();

                System.out.println("发送：" + random + ":CONNECTION" + "--返回心跳: " + receive.toString());
            });
        });
        vertx.setPeriodic(2500, l -> {
            double random = Math.random();
            client.sendMessage(random + ":GEO GO\r\n").addListener(handler -> {
                if (!handler.isSuccess()) {
                    log.error("error", handler.cause());
                    return;
                }
                String receive = (String) handler.get();

                System.out.println("发送：" + random + ":GEO GO" + "--返回应答: " + receive.toString());
            });
        });
    }
}
