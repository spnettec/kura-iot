package com.yofc.test;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.TimeUnit;

@Log4j2
@ExtendWith(VertxExtension.class)
public class VerxSocketTest {

    private VerxSocketClient client;

    @BeforeEach
    @DisplayName("开始")
    public void setUp(Vertx vertx, VertxTestContext context) {
        client = new VerxSocketClient("192.168.0.220", 8000);
        vertx.deployVerticle(client, context.succeeding(id -> context.completeNow()));
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
            System.out.println("返回监听: " + buffer.length() + "个字节");
        });

        /*vertx.setPeriodic(300, t -> {
            client.sendAndReply("EVN2?0\r\n", handler -> {
                if (handler.failed()) {
                    log.error("SMPINF error", handler.cause());
                    return;
                }
                Buffer receive = handler.result();

                System.out.println("发送：" + "SMPINF?" + "--返回: " + receive.toString());
            });
        });*/
        send(vertx);
    }

    private void send(Vertx vertx){
        vertx.setTimer(1000, t -> {
            client.sendAndReply("SMPINF?\r\n", handler -> {
                if (handler.failed()) {
                    log.error("SMPINF error", handler.cause());
                    return;
                }
                Buffer receive = handler.result();

                System.out.println("发送：" + "SMPINF?" + "--返回: " + receive.toString());
                client.sendAndReply("DAT?\r\n", handler1 -> {
                    if (handler1.failed()) {
                        log.error("DAT error", handler1.cause());
                        return;
                    }
                    Buffer receive1 = handler1.result();
                    System.out.println("发送：" + "DAT?" + "--返回: " + receive1.length() + "个字节");
                    send(vertx);
                });
            });
        });
    }
}
