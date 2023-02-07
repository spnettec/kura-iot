package com.yofc.iot.apps.test.ot3001;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.yofc.iot.apps.ot3001.SerialClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
public class TestSearial {

    private static final Logger logger = LoggerFactory.getLogger(TestSearial.class);
    static SerialClient client;
    @BeforeAll
    @DisplayName("开始")
    public static void setUp(Vertx vertx, VertxTestContext context) throws InterruptedException, ExecutionException {
        client = new SerialClient("/dev/cu.usbserial-EHACb11A920",115200,0,0,8,1);
        client.connect();
        context.completeNow();
    }

    @AfterAll
    @DisplayName("结束")
    public static void tearDown(Vertx vertx, VertxTestContext context) {
        client.close();
        context.completeNow();
    }

    @Test
    @Timeout(value = 5000, timeUnit = TimeUnit.SECONDS)
    public void testLogixRead(Vertx vertx, VertxTestContext testContext) throws InterruptedException {
        int result = client.getChannel();
        System.out.println("查询通道:"+result);
        System.out.println("设置通道1:"+client.setChannel(1));

        result = client.getChannel();
        System.out.println("查询通道:"+result);
        testContext.completeNow();
    }
}
