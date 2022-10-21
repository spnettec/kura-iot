package com.yofc.iot.apps.serial;

import com.fazecast.jSerialComm.SerialPort;
import com.yofc.vertx.osgi.api.CRC16Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class SerialDriverClient {

  private static final Logger logger = LoggerFactory.getLogger(SerialDriverClient.class);
  private final ByteBuffer buffer = ByteBuffer.allocate(255);
  private SerialPort comPort = null;
  private String serialPort = "/dev/ttyUSB0";
  private int baudRate = 115200;
  private int flowControl = 0;
  private int parity = 0;
  private int stopBits = 8;

  public SerialDriverClient(
      String serialPort, int baudRate, int flowControl, int parity, int stopBits) {
    this.serialPort = serialPort;
    this.baudRate = baudRate;
    this.flowControl = flowControl;
    this.parity = parity;
    this.stopBits = stopBits;
  }

  public boolean isConnected() {
    return (comPort != null && comPort.isOpen());
  }

  public synchronized void connect() {
    comPort = SerialPort.getCommPort(this.serialPort);
    comPort.setBaudRate(this.baudRate);
    comPort.setFlowControl(this.flowControl);
    comPort.setParity(this.parity);
    comPort.setNumStopBits(this.stopBits);
    comPort.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 0);
    comPort.openPort();
    if (!comPort.isOpen()) {
      throw new RuntimeException("open port faileried");
    }
  }

  public synchronized String readTag(String tagName, int tagReadLength) {
    buffer.clear();
    byte readNo = CRC16Util.HexStr2Byte(tagName);
    byte lengthHi = 0x00;
    byte lengthLow = 0x08;
    byte readCmd = 0x06;
    byte blank = 0x03;
    byte startArea = 0x00;
    byte readLen = (byte) tagReadLength;
    byte[] crcData = {lengthHi, lengthLow, readCmd, blank, startArea, readLen};
    byte[] crc = CRC16Util.getCrcBytes(crcData);
    byte eof = (byte) ~readNo;
    buffer.put(readNo);
    buffer.put(crcData);
    buffer.put(crc);
    buffer.put(eof);
    buffer.flip();
    byte[] result = new byte[buffer.remaining()];
    buffer.get(result);
    if (comPort.bytesAvailable() > 0) {
      byte[] readBuffer = new byte[comPort.bytesAvailable()];
      comPort.readBytes(readBuffer, readBuffer.length);
    }
    int retrunNum = comPort.writeBytes(result, result.length);

    if (retrunNum <= 0) {
      throw new RuntimeException("read sen command error: no bytes sent");
    }

    int bytesNum = comPort.bytesAvailable();
    int tryNum = 0;
    while (bytesNum <= 0 && tryNum < 2) {
      bytesNum = comPort.bytesAvailable();
      tryNum++;
      try {
        Thread.sleep(10);
      } catch (InterruptedException e) {
      }
    }
    if (bytesNum <= 0) {
      logger.error("read error: no bytes return");
      throw new RuntimeException("write error: no bytes return");
    }
    byte[] byes = new byte[bytesNum];
    comPort.readBytes(byes, byes.length);
    if (byes.length < 4) {
      logger.error("read error:return bytes less 4");
      return "";
    }
    if (byes[4] != 0) {
      if (byes[4] == 1) {
        return readTag(tagName, tagReadLength);
      } else {
        String code = CRC16Util.byte2HexStr(byes[4]);
        if (!"80".equals(code)) {
          logger.error("read error,error code:{}", CRC16Util.byte2HexStr(byes[4]));
        }
        return "";
      }
    } else {
      int nums = byes[5];
      byte[] bytestr = new byte[nums];
      System.arraycopy(byes, 6, bytestr, 0, nums);
      return new String(bytestr).trim();
    }
  }

  public synchronized void writeTag(String tagName, String value, Integer tagReadLength) {
    byte[] strBytes = value.getBytes();
    int writeLen = tagReadLength;
    buffer.clear();
    byte[] dataBytes = new byte[writeLen];
    for (int i = 0; i < writeLen; i++) {
      if (i < strBytes.length) {
        dataBytes[i] = strBytes[i];
      } else {
        dataBytes[i] = 0;
      }
    }
    byte readNo = CRC16Util.HexStr2Byte(tagName);
    byte writeCmd = 0x07;
    byte blank = 0x03;
    byte startArea = 0x00;

    byte eof = (byte) ~readNo;
    buffer.put(readNo);
    buffer.putShort((short) 0); // length
    buffer.put(writeCmd);
    buffer.put(blank);
    buffer.put(startArea);
    buffer.put((byte) writeLen);
    buffer.put(dataBytes);
    buffer.putShort((short) 0); // crc
    buffer.put(eof);
    int end = buffer.position();
    buffer.putShort(1, (short) (end - 2));
    byte[] crcDatas = new byte[end - 4];
    buffer.position(1);
    buffer.get(crcDatas);
    int crc = CRC16Util.calcCrc16(crcDatas);
    buffer.order(ByteOrder.LITTLE_ENDIAN);
    buffer.putShort(end - 3, (short) crc);
    buffer.order(ByteOrder.BIG_ENDIAN);
    buffer.limit(end);
    buffer.position(0);
    byte[] result = new byte[buffer.remaining()];
    buffer.get(result);
    while (comPort.bytesAvailable() > 0) {
      byte[] readBuffer = new byte[comPort.bytesAvailable()];
      comPort.readBytes(readBuffer, readBuffer.length);
    }
    comPort.writeBytes(result, result.length);

    int bytesNum = 0;
    int tryNum = 0;
    while ((bytesNum = comPort.bytesAvailable()) <= 0 && tryNum < 200) {
      tryNum++;
      try {
        Thread.sleep(10);
      } catch (InterruptedException e) {
      }
    }
    if (bytesNum <= 0) {
      throw new RuntimeException("write error: no bytes return");
    }
    byte[] byes = new byte[bytesNum];
    comPort.readBytes(byes, byes.length);
    if (byes.length < 4) {
      throw new RuntimeException("write error:return bytes less 4");
    }
    if (byes[4] != 0) {
      if (byes[4] == 1) {
        writeTag(tagName, value, tagReadLength);
      } else {
        throw new RuntimeException("write error: error code:" + CRC16Util.byte2HexStr(byes[4]));
      }
    }
  }

  public synchronized void close() {
    if (comPort != null) {
      comPort.closePort();
    }
    comPort = null;
  }
}
