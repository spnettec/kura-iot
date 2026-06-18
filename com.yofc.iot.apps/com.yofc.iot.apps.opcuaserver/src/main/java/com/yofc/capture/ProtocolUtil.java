package com.yofc.capture;

import org.apache.plc4x.java.s7.readwrite.TPKTPacket;
import org.apache.plc4x.java.spi.buffers.api.exceptions.BufferException;
import org.apache.plc4x.java.spi.buffers.bytebased.ReadBufferByteBased;
import org.apache.plc4x.java.spi.buffers.bytebased.WithByteBasedOption;

public class ProtocolUtil {

    private ProtocolUtil() {
        throw new IllegalStateException("Utility class");
    }

    public static boolean isS7Packet0(byte[] packet) {
        if (packet.length < 4 && packet[0] != 0x03) {
            return false;
        }
        int len = (4 << packet[2]) | (packet[3] & 0xff);
        return packet.length == len;
    }

    public static boolean isS7Packet(byte[] packet) {
        ReadBufferByteBased readBuffer = new ReadBufferByteBased(packet, WithByteBasedOption.WithByteOrder("BIG_ENDIAN"));
        TPKTPacket tPKTPacket = null;
        try {
            tPKTPacket = TPKTPacket.staticParse(readBuffer);
        } catch (BufferException e) {
        }
        return tPKTPacket != null;
    }
}
