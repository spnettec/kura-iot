package com.yofc.test;

import io.vertx.core.Vertx;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Log4j2
@ExtendWith(VertxExtension.class)
class NettySocketTest {

  private static NettySocketClient client;
  private static MockTcpServer mockServer;

  @BeforeAll
  static void startMockServer() throws Exception {
    mockServer = MockTcpServer.start();
  }

  @AfterAll
  static void stopMockServer() throws Exception {
    if (mockServer != null) {
      mockServer.close();
    }
  }

  @BeforeEach
  @DisplayName("开始")
  public void setUp(Vertx vertx, VertxTestContext context) {
    client = new NettySocketClient("127.0.0.1", mockServer.getMappedPort());
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
  @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
  @DisplayName("Netty测试")
  public void testMyApplication(Vertx vertx, VertxTestContext context) {

    client.addLister(
        msg -> {
          System.out.println("返回监听: " + Arrays.toString(msg));
        });
    /*vertx.setPeriodic(
        1000,
        l -> {
          client
              .sendMessage("SMPINF?\r\n")
              .addListener(
                  handler -> {
                    if (!handler.isSuccess()) {
                      context.failNow(handler.cause());
                      return;
                    }
                    byte[] receive = (byte[]) handler.get();

                    System.out.println("发送：" + "SMPINF" + "--返回: " + new String(receive, StandardCharsets.UTF_8));
                  });
        });*/
   // vertx.setPeriodic(2500, l ->
      client
              .sendMessage("SMPINF?")
              .addListener(
                      handler -> {
                          if (!handler.isSuccess()) {
                              context.failNow(handler.cause());
                              return;
                          }
                          byte[] receive = (byte[]) handler.get();
                          org.junit.jupiter.api.Assertions.assertEquals("SMPINF:OK", new String(receive, StandardCharsets.UTF_8));

                          System.out.println("发送：" + "SMPINF" + "--返回: " + new String(receive, StandardCharsets.UTF_8));
                          client
                                  .sendMessage("DAT?")
                                  .addListener(
                                          handler1 -> {
                                              if (!handler1.isSuccess()) {
                                                  context.failNow(handler1.cause());
                                                  return;
                                              }
                                              byte[] receive1 = (byte[]) handler1.get();

                                              System.out.println("发送：" + "DAT?" + "--返回: " + Arrays.toString(receive1));
                                              context.verify(() -> org.junit.jupiter.api.Assertions.assertEquals("0123456789", new String(receive1, StandardCharsets.UTF_8)));
                                              context.completeNow();
                                          });
                      });

  //);

    }
}
