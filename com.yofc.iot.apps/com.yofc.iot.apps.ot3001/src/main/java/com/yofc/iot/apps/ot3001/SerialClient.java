package com.yofc.iot.apps.ot3001;

import com.fazecast.jSerialComm.SerialPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SerialClient {

    private static final Logger logger = LoggerFactory.getLogger(SerialClient.class);
    private SerialPort comPort = null;
    private String serialPort = "/dev/ttyUSB0";
    private int baudRate = 115200;
    private int flowControl = 0;
    private int parity = 0;
    private int dataBits = 8;
    private int stopBits = 1;

    private final byte[] channelBytes = new byte[] { (byte) 0xA5, (byte) 0xA5, 0x00, 0x02, 0x02, (byte) 0xF0 };

    private int channel = -1;

    public SerialClient(String serialPort, int baudRate, int flowControl, int parity, int dataBits, int stopBits) {
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
        comPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING | SerialPort.TIMEOUT_WRITE_BLOCKING, 10, 0);
        comPort.openPort();
        if (!comPort.isOpen()) {
            throw new RuntimeException("open port faileried");
        }
    }

    public synchronized byte[] sendCommand(byte[] command, int readLenth) {
        if (comPort.bytesAvailable() > 0) {
            byte[] readBuffer = new byte[comPort.bytesAvailable()];
            comPort.readBytes(readBuffer, readBuffer.length);
        }
        int returnNum = comPort.writeBytes(command, command.length);

        if (returnNum <= 0) {
            throw new RuntimeException("send command error: no bytes sent");
        }
        if (readLenth <= 0) {
            readLenth = comPort.bytesAvailable();
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
        return sendCommand(command, 0);
    }

    public synchronized void close() {
        if (comPort != null) {
            comPort.closePort();
        }
        comPort = null;
    }

    public int queryChannel() {
        channelBytes[5] = (byte) 0xF0;
        byte[] result = this.sendCommand(channelBytes, 1);
        int resultNo;
        if (result == null || result.length == 0) {
            resultNo = -1;
        } else {
            resultNo = result[0];
        }
        this.channel = resultNo;
        return this.channel;
    }

    public int getChannel() {
        if (this.channel == -1) {
            return queryChannel();
        }
        return this.channel;
    }

    public boolean setChannel(int channelNo) {
        channelBytes[5] = (byte) channelNo;
        byte[] result = this.sendCommand(channelBytes, 1);
        int resultNo;
        if (result == null || result.length == 0) {
            resultNo = getChannel();
        } else {
            resultNo = result[0];
        }
        this.channel = resultNo;
        if (channelNo != resultNo) {
            return false;
        }
        return true;
    }
}
