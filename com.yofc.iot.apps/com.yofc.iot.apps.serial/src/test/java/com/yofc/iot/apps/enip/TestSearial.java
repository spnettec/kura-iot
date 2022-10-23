package com.yofc.iot.apps.enip;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import com.yofc.vertx.osgi.api.CRC16Util;

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
        client = new SerialClient("/dev/tty.usbserial-1440",115200,0,0,8,1);
        client.connect();
        context.completeNow();
    }

    @AfterAll
    @DisplayName("结束")
    public static void tearDown(Vertx vertx, VertxTestContext context) {
        context.completeNow();
    }

    @Test
    @Timeout(value = 5000, timeUnit = TimeUnit.SECONDS)
    public void testLogixRead(Vertx vertx, VertxTestContext testContext) throws InterruptedException {
        byte[] result = client.sendCommand(new byte[]{ (byte) 0xA5, (byte) 0xA5, 0x00, 0x02, 0x02, (byte) 0xF0 },1);
        System.out.println(Arrays.toString(result));
        /*result = client.sendCommand(new byte[]{ (byte) 0xA5, (byte) 0xA5, 0x00, 0x02, 0x02, (byte) 1 },1);
        System.out.println(Arrays.toString(result));
        result = client.sendCommand(new byte[]{ (byte) 0xA5, (byte) 0xA5, 0x00, 0x02, 0x02, (byte) 2 },1);
        System.out.println(Arrays.toString(result));
        result = client.sendCommand(new byte[]{ (byte) 0xA5, (byte) 0xA5, 0x00, 0x02, 0x02, (byte) 3 },1);
        System.out.println(Arrays.toString(result));
        result = client.sendCommand(new byte[]{ (byte) 0xA5, (byte) 0xA5, 0x00, 0x02, 0x02, (byte) 4 },1);
        System.out.println(Arrays.toString(result));
        result = client.sendCommand(new byte[]{ (byte) 0xA5, (byte) 0xA5, 0x00, 0x02, 0x02, (byte) 5 },1);
        System.out.println(Arrays.toString(result));
        result = client.sendCommand(new byte[]{ (byte) 0xA5, (byte) 0xA5, 0x00, 0x02, 0x02, (byte) 6 },1);
        System.out.println(Arrays.toString(result));
        result = client.sendCommand(new byte[]{ (byte) 0xA5, (byte) 0xA5, 0x00, 0x02, 0x02, (byte) 0xF0 },1);
        System.out.println(Arrays.toString(result));*/
        testContext.completeNow();
    }
}
