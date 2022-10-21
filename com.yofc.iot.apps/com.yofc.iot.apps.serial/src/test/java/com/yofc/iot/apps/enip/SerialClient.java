package com.yofc.iot.apps.enip;

import com.fazecast.jSerialComm.SerialPort;
import com.yofc.vertx.osgi.api.CRC16Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class SerialClient {

  private static final Logger logger = LoggerFactory.getLogger(SerialClient.class);
  private final ByteBuffer buffer = ByteBuffer.allocate(255);
  private SerialPort comPort = null;
  private String serialPort = "/dev/ttyUSB0";
  private int baudRate = 115200;
  private int flowControl = 0;
  private int parity = 0;
  private int dataBits = 8;
  private int stopBits = 1;



  public SerialClient(
      String serialPort, int baudRate, int flowControl, int parity,int dataBits, int stopBits) {
    this.serialPort = serialPort;
    this.baudRate = baudRate;
    this.flowControl = flowControl;
    this.parity = parity;
    this.dataBits = dataBits;
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
    comPort.setNumDataBits(this.dataBits);
    comPort.setNumStopBits(this.stopBits);
    comPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 10, 0);
    comPort.openPort();
    if (!comPort.isOpen()) {
      throw new RuntimeException("open port faileried");
    }
  }

  public synchronized byte[] sendCommand(byte[] command, int readLenth) {
    buffer.clear();
    buffer.put(command);
    buffer.flip();
    byte[] result = new byte[buffer.remaining()];
    buffer.get(result);
    if (comPort.bytesAvailable() > 0) {
      byte[] readBuffer = new byte[comPort.bytesAvailable()];
      comPort.readBytes(readBuffer, readBuffer.length);
    }
    int returnNum = comPort.writeBytes(result, result.length);

    if (returnNum <= 0) {
      throw new RuntimeException("send command error: no bytes sent");
    }
    if(readLenth <= 0) {
      readLenth = comPort.bytesAvailable();
      int tryNum = 0;
      while (readLenth <= 0 && tryNum < 10) {
        readLenth = comPort.bytesAvailable();
        tryNum++;
        try {
          Thread.sleep(10);
        } catch (InterruptedException e) {
        }
      }
      if (readLenth <= 0) {
        logger.error("read error: no bytes return");
        throw new RuntimeException("write error: no bytes return");
      }
    }
    byte[] byes = new byte[readLenth];
    comPort.readBytes(byes, readLenth);
    return byes;
  }
  public byte[] sendCommand(byte[] command) {
    return sendCommand(command,0);
  }

  public synchronized void close() {
    if (comPort != null) {
      comPort.closePort();
    }
    comPort = null;
  }
}
