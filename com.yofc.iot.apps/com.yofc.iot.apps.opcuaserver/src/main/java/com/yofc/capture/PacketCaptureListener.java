package com.yofc.capture;

import org.pcap4j.core.PacketListener;
import org.pcap4j.packet.Packet;
import org.pcap4j.packet.UnknownPacket;

public class PacketCaptureListener implements PacketListener {

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    private Callback callback;

    @Override
    public void gotPacket(Packet packet) {
        Packet unkonwPacket = packet.get(UnknownPacket.class);
        if (callback != null) {
            callback.call(unkonwPacket, packet);
        }
    }

    @FunctionalInterface
    public interface Callback {

        void call(Packet packet, Packet rawPacket);
    }
}
