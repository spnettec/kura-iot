package com.yofc.test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yofc.netty.fsm.tcp.NettyTcpClient;
import com.yofc.netty.fsm.tcp.TcpNetIpClientConfig;

import io.vertx.core.Vertx;
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
    @Timeout(value = 1000, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Netty测试")
    public void testNetty(Vertx vertx, VertxTestContext context) throws InterruptedException, TimeoutException {
        NettyTcpClient client = new NettyTcpClient(
                TcpNetIpClientConfig.builder("10.211.55.3", 3000).setLazy(true).build());
        CompletableFuture<NettyTcpClient> future = client.connect();
        future.thenAccept(action -> {
            logger.debug(action.getState());
        });

    }
}