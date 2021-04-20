package com.yofc.iot.apps.enip;

import io.vertx.core.Vertx;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@ExtendWith(VertxExtension.class)
public class TestSearial {

  private static final Logger logger = LoggerFactory.getLogger(TestSearial.class);

  @BeforeAll
  @DisplayName("开始")
  public static void setUp(Vertx vertx, VertxTestContext context)
      throws InterruptedException, ExecutionException {

    context.completeNow();
  }

  @AfterAll
  @DisplayName("结束")
  public static void tearDown(Vertx vertx, VertxTestContext context) {
    context.completeNow();
  }

  @Test
  @Timeout(value = 10000000, timeUnit = TimeUnit.SECONDS)
  public void testLogixRead(Vertx vertx, VertxTestContext context) throws InterruptedException {}
}
