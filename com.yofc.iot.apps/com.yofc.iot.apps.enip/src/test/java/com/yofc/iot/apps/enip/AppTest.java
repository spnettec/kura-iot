package com.yofc.iot.apps.enip;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
import com.yofc.iot.apps.enip.cip.epath.EPath;
import com.yofc.iot.apps.enip.cip.epath.PortSegment;
import com.yofc.iot.apps.enip.cip.services.CipService;
import com.yofc.iot.apps.enip.cip.services.MultipleServicePacketService;
import com.yofc.iot.apps.enip.cip.structs.CIPData;
import com.yofc.iot.apps.enip.cip.structs.CIPData.Type;
import com.yofc.iot.apps.enip.logix.services.ReadTagFragmentedService;
import com.yofc.iot.apps.enip.logix.services.WriteTagService;
import com.yofc.vertx.osgi.api.Utils;

import io.netty.buffer.ByteBuf;
import io.vertx.core.Vertx;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class AppTest {

    private static final Logger logger = LoggerFactory.getLogger(AppTest.class);
    static CipConnectionPool pool;
    static CipClient client;

    @BeforeAll
    @DisplayName("开始")
    public static void setUp(Vertx vertx, VertxTestContext context) throws InterruptedException, ExecutionException {
        EtherNetIpClientConfig config = EtherNetIpClientConfig.builder("10.166.11.181").setSerialNumber(0x00)
                .setVendorId(0x00).setTimeout(Duration.ofSeconds(500)).build();

        // backplane, slot 0
        EPath.PaddedEPath connectionPath = new EPath.PaddedEPath(new PortSegment(1, new byte[] { (byte) 0 }));

        client = new CipClient(config, connectionPath);

        client.connect().get();

        pool = new CipConnectionPool(2, client, connectionPath, 500);

        // the tag we'll use as an example
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
        // client.listIdentity().whenComplete((identity, ex) -> {
        // if (identity != null)
        // logger.info("identity: {}", identity.getIdentity().get().toString());
        // else
        // ex.printStackTrace();
        // });
        ReadTagFragmentedService service = new ReadTagFragmentedService("FURNACE_MOTOR_MODE_SELECT_HMI");
        ReadTagFragmentedService service1 = new ReadTagFragmentedService("SYSTEM_BAR_NO");
        List<CipService<?>> services = new ArrayList<>();
        services.add(service);
        services.add(service1);
        MultipleServicePacketService s = new MultipleServicePacketService(services);
        pool.acquire().whenComplete(Utils.BiConsumerWrapper((connection, ex) -> {
            if (connection != null) {
                CompletableFuture<ByteBuf[]> f = client.invokeConnected(connection.getO2tConnectionId(), s);

                f.whenComplete(Utils.BiConsumerWrapper((data, ex2) -> {
                    if (data != null) {
                        for (int i = 0; i < data.length; i++) {
                            ByteBuf d = data[i];
                            if (d != null) {
                                try {
                                    final CIPData.Type type = CIPData.Type.forCode(d.readShortLE());
                                    CIPData cipdata = new CIPData(type, d);
                                    logger.info("Tag data: {}", cipdata.toString());
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    } else {
                        ex2.printStackTrace();
                    }
                    pool.release(connection);
                    context.completeNow();
                }));
            } else {
                ex.printStackTrace();
                context.completeNow();
            }
        }));

    }

    @Test
    @Timeout(value = 1000, timeUnit = TimeUnit.SECONDS)
    public void testLogixWrite(Vertx vertx, VertxTestContext context) throws Exception {
        WriteTagService service = new WriteTagService("FURNACE_MOTOR_MODE_SELECT_HMI",
                new CIPData(Type.INT).set(0, 44));
        WriteTagService service1 = new WriteTagService("SYSTEM_BAR_NO", new CIPData(
                "asasa1111111111111111111111111111111111111111111111111111111111111111111111111eeddccbbaaqq"));
        List<CipService<?>> services = new ArrayList<>();
        services.add(service);
        services.add(service1);
        MultipleServicePacketService s = new MultipleServicePacketService(services);
        pool.acquire().whenComplete(Utils.BiConsumerWrapper((connection, ex0) -> {
            if (connection != null) {
                CompletableFuture<ByteBuf[]> f = client.invokeConnected(connection.getO2tConnectionId(), s);

                f.whenComplete(Utils.BiConsumerWrapper((v, ex) -> {
                    if (ex != null) {
                        ex.printStackTrace();
                    } else {
                        logger.info("WriteTagService completed.");
                    }

                    pool.release(connection);
                    context.completeNow();
                }));
            } else {
                ex0.printStackTrace();
                context.completeNow();
            }
        }));
    }
}