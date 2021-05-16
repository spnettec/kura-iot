package com.yofc.iot.apps.enip;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import com.yofc.vertx.osgi.api.CRC16Util;
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

import java.nio.ByteBuffer;
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
  public void testLogixRead(Vertx vertx, VertxTestContext context) throws InterruptedException {
    SerialPort comPort = SerialPort.getCommPort("/dev/tty.usbserial-1420");
    comPort.setBaudRate(115200);
    comPort.setFlowControl(0);
    comPort.setParity(0);
    comPort.setNumStopBits(8);
    comPort.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 0);
    comPort.openPort();
    if (!comPort.isOpen()) {
      System.out.println("open port error");
      return;
    }
    comPort.addDataListener(
        new SerialPortDataListener() {

          @Override
          public int getListeningEvents() {
            return SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
          }

          @Override
          public void serialEvent(SerialPortEvent event) {
            if (event.getEventType() != SerialPort.LISTENING_EVENT_DATA_AVAILABLE) return;
            byte[] newData = new byte[comPort.bytesAvailable()];
            comPort.readBytes(newData, newData.length);
            decodeData(newData, comPort);
            sendData(comPort);
          }
        });
    sendData(comPort);
    // read(comPort);
    // read(comPort);
    // read(comPort);
    // read(comPort);
    // read(comPort);
    // read(comPort);
    // read(comPort);
    // read(comPort);
    // read(comPort);
    // read(comPort);
    // comPort.closePort();
    // context.completeNow();
  }

  private void sendData(SerialPort comPort) {
    ByteBuffer buffer = ByteBuffer.allocate(255);
    byte readNo = (byte) 0xA1;

    byte lengthHi = 0x00;
    byte lengthLow = 0x08;
    byte readCmd = 0x06;
    byte blank = 0x03;
    byte startArea = 0x00;
    byte readLen = 12;
    byte[] crcData = {lengthHi, lengthLow, readCmd, blank, startArea, readLen};
    // System.out.print(CRC16Util.getCrc(CRC16Util.calcCrc16(crcData)));
    byte[] crc = CRC16Util.getCrcBytes(crcData);
    byte eof = (byte) ~readNo;
    buffer.put(readNo);
    buffer.put(crcData);
    buffer.put(crc);
    buffer.put(eof);
    buffer.flip();
    byte[] result = new byte[buffer.remaining()];
    buffer.get(result);

    comPort.writeBytes(result, result.length);
  }

  private void decodeData(byte[] byes, SerialPort comPort) {
    if (byes.length < 4) {
      System.out.println("ERROR:" + CRC16Util.bytes2HexStr(byes));
      return;
    }
    if (byes[4] != 0) {
      if (byes[4] == 1) {
        // sendData(comPort);
        return;
      }
      System.out.println("ERROR:" + CRC16Util.bytes2HexStr(byes));
    } else {
      int nums = byes[5];
      byte[] bytestr = new byte[nums];
      for (int i = 0; i < nums; i++) bytestr[i] = byes[6 + i];
      System.out.println(new String(bytestr));
      // System.out.println(CRC16Util.byte2HexStr(byes));
    }
  }

  private void read(SerialPort comPort) throws InterruptedException {
    ByteBuffer buffer = ByteBuffer.allocate(255);
    byte readNo = (byte) 0xA1;

    byte lengthHi = 0x00;
    byte lengthLow = 0x08;
    byte readCmd = 0x06;
    byte blank = 0x03;
    byte startArea = 0x00;
    byte readLen = 12;
    byte[] crcData = {lengthHi, lengthLow, readCmd, blank, startArea, readLen};
    // System.out.print(CRC16Util.getCrc(CRC16Util.calcCrc16(crcData)));
    byte[] crc = CRC16Util.getCrcBytes(crcData);
    byte eof = (byte) ~readNo;
    buffer.put(readNo);
    buffer.put(crcData);
    buffer.put(crc);
    buffer.put(eof);
    buffer.flip();
    byte[] result = new byte[buffer.remaining()];
    buffer.get(result);
    // System.out.println(CRC16Util.byte2HexStr(result));
    while (comPort.bytesAvailable() > 0) {
      byte[] readBuffer = new byte[comPort.bytesAvailable()];
      comPort.readBytes(readBuffer, readBuffer.length);
    }

    comPort.writeBytes(result, result.length);
    int bytesNum = 0;
    while ((bytesNum = comPort.bytesAvailable()) <= 0) Thread.sleep(10);
    byte[] byes = new byte[bytesNum];
    comPort.readBytes(byes, byes.length);

    if (byes[4] != 0) {
      if (byes[4] == 1) {
        read(comPort);
        return;
      }
      System.out.println("ERROR:" + CRC16Util.bytes2HexStr(byes));
    } else {
      int nums = byes[5];
      byte[] bytestr = new byte[nums];
      for (int i = 0; i < nums; i++) bytestr[i] = byes[6 + i];
      System.out.println(new String(bytestr));
      // System.out.println(CRC16Util.byte2HexStr(byes));
    }
  }
}
