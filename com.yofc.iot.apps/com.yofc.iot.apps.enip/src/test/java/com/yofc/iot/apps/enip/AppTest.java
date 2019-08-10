package com.yofc.iot.apps.enip;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yofc.iot.apps.enip.cip.CipClient;
import com.yofc.iot.apps.enip.cip.CipConnectionPool;
import com.yofc.iot.apps.enip.cip.CipDataType;
import com.yofc.iot.apps.enip.cip.epath.DataSegment;
import com.yofc.iot.apps.enip.cip.epath.EPath;
import com.yofc.iot.apps.enip.cip.epath.PortSegment;
import com.yofc.iot.apps.enip.cip.structs.CIPData;
import com.yofc.iot.apps.enip.logix.services.ReadTagService;
import com.yofc.iot.apps.enip.logix.services.WriteTagService;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.vertx.core.Vertx;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class AppTest {

    private static final Logger logger = LoggerFactory.getLogger(AppTest.class);
    static EPath.PaddedEPath requestPath;
    static CipConnectionPool pool;
    static CipClient client;

    @BeforeAll
    @DisplayName("开始")
    public static void setUp(Vertx vertx, VertxTestContext context) throws InterruptedException, ExecutionException {
        EtherNetIpClientConfig config = EtherNetIpClientConfig.builder("10.166.11.181").setSerialNumber(0x00)
                .setVendorId(0x00).setTimeout(Duration.ofSeconds(2)).build();

        // backplane, slot 0
        EPath.PaddedEPath connectionPath = new EPath.PaddedEPath(new PortSegment(1, new byte[] { (byte) 0 }));

        client = new CipClient(config, connectionPath);

        client.connect().get();

        pool = new CipConnectionPool(2, client, connectionPath, 500);

        // the tag we'll use as an example
        requestPath = new EPath.PaddedEPath(new DataSegment.AnsiDataSegment("FURNACE_MOTOR_MODE_SELECT_HMI"));
        context.completeNow();
    }

    @AfterAll
    @DisplayName("结束")
    public static void tearDown(Vertx vertx, VertxTestContext context) {
        context.completeNow();
    }

    @Test
    @Timeout(value = 1000, timeUnit = TimeUnit.SECONDS)
    public void testLogixRead(Vertx vertx, VertxTestContext context) {

        ReadTagService service = new ReadTagService(requestPath);

        pool.acquire().whenComplete((connection, ex) -> {
            if (connection != null) {
                CompletableFuture<ByteBuf> f = client.invokeConnected(connection.getO2tConnectionId(), service);

                f.whenComplete((data, ex2) -> {
                    if (data != null) {
                        final CIPData.Type type = CIPData.Type.forCode(data.readShort());
                        CIPData cipdata = new CIPData(type, data);
                        try {
                            logger.info("Tag data: {}", cipdata.toString());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else {
                        ex2.printStackTrace();
                    }
                    pool.release(connection);
                    context.completeNow();
                });
            } else {
                ex.printStackTrace();
            }
        });

    }

    @Test
    @Timeout(value = 1000, timeUnit = TimeUnit.SECONDS)
    public void testLogixWrite(Vertx vertx, VertxTestContext context) {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeIntLE(43);

        WriteTagService writeTagService = new WriteTagService(requestPath, false, CipDataType.INT.getCode(), buffer);

        pool.acquire().thenAccept(connection -> {
            CompletableFuture<Void> f = client.invokeConnected(connection.getO2tConnectionId(), writeTagService);

            f.whenComplete((v, ex) -> {
                if (ex != null) {
                    ex.printStackTrace();
                } else {
                    logger.info("WriteTagService completed.");
                }

                pool.release(connection);
                context.completeNow();
            });
        });
    }
}