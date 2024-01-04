package com.yofc.capture;

import org.apache.plc4x.java.s7.readwrite.TPKTPacket;
import org.apache.plc4x.java.spi.generation.ByteOrder;
import org.apache.plc4x.java.spi.generation.ParseException;
import org.apache.plc4x.java.spi.generation.ReadBufferByteBased;

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
        ReadBufferByteBased readBuffer = new ReadBufferByteBased(packet, ByteOrder.BIG_ENDIAN);
        TPKTPacket tPKTPacket = null;
        try {
            tPKTPacket = TPKTPacket.staticParse(readBuffer);
        } catch (ParseException e) {
        }
        return tPKTPacket != null;
    }
}
