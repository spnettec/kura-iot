package etherip;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import etherip.types.CIPData;
import io.vertx.core.Vertx;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class AppTest {

    private static final Logger logger = LoggerFactory.getLogger(AppTest.class);
    static EtherNetIP client;

    @BeforeAll
    @DisplayName("开始")
    public static void setUp(Vertx vertx, VertxTestContext context) throws InterruptedException, ExecutionException {
        try {
            client = new EtherNetIP("192.168.50.36", 0);
            client.connectTcp();
        } catch (final Exception e) {
            fail("Failed with CipException");
        }
        context.completeNow();
    }

    @AfterAll
    @DisplayName("结束")
    public static void tearDown(Vertx vertx, VertxTestContext context) {
        try {
            client.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        context.completeNow();
    }

    // @Test
    // @Timeout(value = 10000000, timeUnit = TimeUnit.SECONDS)
    public void testLogixRead(Vertx vertx, VertxTestContext context) {
        vertx.setPeriodic(100, l -> {
            final String[] tags = new String[] { "B1", "B2", "DI1", "DI2", "I1", "I2", "R1", "R2" };
            CIPData[] results = null;
            try {
                results = client.readTags(tags);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (results != null)
                for (CIPData cipData : results)
                    logger.info(cipData.toString());
        });
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    public void testLogixWrite(Vertx vertx, VertxTestContext context) {
        // vertx.setPeriodic(100, l -> {
        try {
            final String[] tags = new String[] { "B2", "I2", };
            CIPData b2Data = new CIPData(CIPData.Type.BOOL);
            b2Data.set(0, 1);
            CIPData i2Data;

            i2Data = new CIPData(CIPData.Type.INT);

            i2Data.set(0, 3);
            CIPData[] results = new CIPData[] { b2Data, i2Data };
            client.writeTags(tags, results);
            context.completeNow();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // });
    }

    // @Test
    // @Timeout(value = 10000000, timeUnit = TimeUnit.SECONDS)
    public void testReadTags(Vertx vertx, VertxTestContext context) {
        final String[] tags = new String[] { "FURNACE_MOTOR_MODE_SELECT_HMI", "FURNACE_MOTOR_MODE_SELECT_HMI",
                "FURNACE_MOTOR_MODE_SELECT_HMI", "FURNACE_MOTOR_MODE_SELECT_HMI", "FURNACE_MOTOR_MODE_SELECT_HMI",
                "SYSTEM_BAR_NO", "SYSTEM_BAR_NO", "SYSTEM_BAR_NO", "SYSTEM_BAR_NO", "SYSTEM_BAR_NO", "SYSTEM_BAR_NO",
                "SYSTEM_BAR_NO", "SYSTEM_BAR_NO", "SYSTEM_BAR_NO", "SYSTEM_BAR_NO", "FURNACE_MOTOR_MODE_SELECT_HMI" };
        CIPData[] results = null;
        try {
            results = client.readTags(tags);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (results != null)
            for (CIPData cipData : results)
                logger.info(cipData.toString());
        context.completeNow();
    }
}
