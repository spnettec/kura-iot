package com.yofc.test;

import io.vertx.core.Vertx;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Log4j2
@ExtendWith(VertxExtension.class)
class NettySocketTest {

  private static NettySocketClient client;

  @BeforeEach
  @DisplayName("开始")
  public void setUp(Vertx vertx, VertxTestContext context) {
    client = new NettySocketClient("192.168.0.220", 8000);
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

    client.addLister(
        msg -> {
          System.out.println("返回监听: " + Arrays.toString(msg));
        });
    vertx.setPeriodic(
        1000,
        l -> {
          client
              .sendMessage("SMPINF?\r\n")
              .addListener(
                  handler -> {
                    if (!handler.isSuccess()) {
                      log.error("SMPINF error", handler.cause());
                      return;
                    }
                    byte[] receive = (byte[]) handler.get();

                    System.out.println("发送：" + "SMPINF" + "--返回: " + Arrays.toString(receive));
                  });
        });
    vertx.setPeriodic(
        2500,
        l -> {
          client
              .sendMessage("DAT?\r\n")
              .addListener(
                  handler -> {
                    if (!handler.isSuccess()) {
                      log.error("error", handler.cause());
                      return;
                    }
                      byte[] receive = (byte[]) handler.get();

                    System.out.println("发送：" + "DAT?" + "--返回: " + Arrays.toString(receive));
                  });
        });
  }
}
