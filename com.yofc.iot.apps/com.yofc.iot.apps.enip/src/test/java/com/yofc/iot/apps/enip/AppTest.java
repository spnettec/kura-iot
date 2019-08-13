package com.yofc.iot.apps.enip;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yofc.iot.apps.enip.cip.structs.CIPData;
import com.yofc.iot.apps.enip.cip.structs.CIPData.Type;
import com.yofc.iot.apps.enip.driver.EIPDriverClient;

import io.vertx.core.Vertx;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class AppTest {

    private static final Logger logger = LoggerFactory.getLogger(AppTest.class);
    static EIPDriverClient client;

    @BeforeAll
    @DisplayName("开始")
    public static void setUp(Vertx vertx, VertxTestContext context) throws InterruptedException, ExecutionException {
        client = new EIPDriverClient("10.166.11.181", 0);
        client.setTimeoutMs(50000);
        client.connect();
        context.completeNow();
    }

    @AfterAll
    @DisplayName("结束")
    public static void tearDown(Vertx vertx, VertxTestContext context) {
        context.completeNow();
    }

    @Test
    @Timeout(value = 100000, timeUnit = TimeUnit.SECONDS)
    public void testLogixRead(Vertx vertx, VertxTestContext context) {
        client.readTags("FURNACE_MOTOR_MODE_SELECT_HMI", "FURNACE_MOTOR_MODE_SELECT_HMI",
                "FURNACE_MOTOR_MODE_SELECT_HMI", "FURNACE_MOTOR_MODE_SELECT_HMI", "FURNACE_MOTOR_MODE_SELECT_HMI",
                "FURNACE_MOTOR_MODE_SELECT_HMI", "SYSTEM_BAR_NO", "SYSTEM_BAR_NO", "SYSTEM_BAR_NO", "SYSTEM_BAR_NO",
                "FURNACE_MOTOR_MODE_SELECT_HMI", "SYSTEM_BAR_NO", "SYSTEM_BAR_NO", "SYSTEM_BAR_NO", "SYSTEM_BAR_NO",
                "FURNACE_MOTOR_MODE_SELECT_HMI", "SYSTEM_BAR_NO", "SYSTEM_BAR_NO", "SYSTEM_BAR_NO", "SYSTEM_BAR_NO",
                "FURNACE_MOTOR_MODE_SELECT_HMI", "SYSTEM_BAR_NO", "SYSTEM_BAR_NO", "SYSTEM_BAR_NO", "SYSTEM_BAR_NO",
                "FURNACE_MOTOR_MODE_SELECT_HMI", "SYSTEM_BAR_NO", "SYSTEM_BAR_NO", "SYSTEM_BAR_NO", "SYSTEM_BAR_NO",
                "FURNACE_MOTOR_MODE_SELECT_HMI", "SYSTEM_BAR_NO", "SYSTEM_BAR_NO", "SYSTEM_BAR_NO", "SYSTEM_BAR_NO",
                "FURNACE_MOTOR_MODE_SELECT_HMI", "SYSTEM_BAR_NO", "SYSTEM_BAR_NO", "SYSTEM_BAR_NO", "SYSTEM_BAR_NO",
                "FURNACE_MOTOR_MODE_SELECT_HMI", "SYSTEM_BAR_NO", "SYSTEM_BAR_NO", "SYSTEM_BAR_NO", "SYSTEM_BAR_NO",
                "FURNACE_MOTOR_MODE_SELECT_HMI", "SYSTEM_BAR_NO", "SYSTEM_BAR_NO", "SYSTEM_BAR_NO", "SYSTEM_BAR_NO",
                "SYSTEM_BAR_NO").whenComplete((data, ex) -> {
                    if (data != null)
                        for (CIPData cipData : data)
                            logger.info("Tag data: {}", cipData.toString());
                    else
                        ex.printStackTrace();
                    context.completeNow();
                });
    }

    @Test
    @Timeout(value = 1000, timeUnit = TimeUnit.SECONDS)
    public void testLogixWrite(Vertx vertx, VertxTestContext context) throws Exception {
        String[] tags = { "FURNACE_MOTOR_MODE_SELECT_HMI", "SYSTEM_BAR_NO" };
        CIPData[] datas = { new CIPData(Type.INT).set(0, 44), new CIPData(
                "asasa1111111111111111111111111111111111111111111111111111111111111111111111111eeddccbbaaqq") };
        client.writeTags(tags, datas).whenComplete((data, ex) -> {
            if (ex == null)
                logger.info("WriteTagService completed.");
            else
                ex.printStackTrace();
            context.completeNow();
        });

    }
}