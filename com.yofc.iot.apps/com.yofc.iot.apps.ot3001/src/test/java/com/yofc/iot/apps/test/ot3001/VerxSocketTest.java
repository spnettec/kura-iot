package com.yofc.iot.apps.test.ot3001;

import com.yofc.iot.apps.ot3001.VerxSocketClient;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Log4j2
@ExtendWith(VertxExtension.class)
@Disabled("Requires a physical OT3001 device at 192.168.0.220:8000; replace with a mock/Docker-backed test later.")
public class VerxSocketTest {

    private VerxSocketClient client;

    @BeforeEach
    @DisplayName("开始")
    public void setUp(Vertx vertx, VertxTestContext context) {
        client = new VerxSocketClient("192.168.0.220", 8000);
        client.vertx(vertx).reconnectAttempts(0).connect().onSuccess(v -> context.completeNow());

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

    private void send(Vertx vertx) {
        client.sendAndReply("STP?\r\n", handler -> {
            Buffer receive = handler.result();
            System.out.println("发送STP? 返回 ：" + receive);
        });
        //vertx.setPeriodic(50, t -> {
            client.sendAndReply("SMPINF?\r\n", handler -> {
                if (handler.failed()) {
                    log.error("STP error", handler.cause());
                    return;
                }
                Buffer receive = handler.result();
                String[] result = receive.toString().split("[\\s,]+");
                if(result.length<3){
                    log.error("SMPINF result delta error");
                    return;
                }
                double deltax = Double.parseDouble(result[2]);
                client.sendAndReply("DAT?\r\n", handler1 -> {
                    if (handler1.failed()) {
                        log.error("DAT error", handler1.cause());
                        return;
                    }
                    Buffer receive1 = handler1.result();
                    int length = receive1.length() / 2;
                    Double[] X = new Double[length];
                    Double[] Y = new Double[length];
                    for (int i = 0; i < length; i++) {
                        X[i] = (i) * deltax / 1000;
                        Y[i] = receive1.getShort(i*2) / 1000d;
                    }
                    System.out.println("delta：" + deltax);
                    //System.out.println("返回 X: " + Arrays.toString(X));
                    System.out.println("返回 Y: " + Arrays.toString(Y));
                });
            });
        //});
    }
}
