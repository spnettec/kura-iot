package com.yofc.capture;

import java.io.EOFException;
import java.util.Random;
import java.util.concurrent.TimeoutException;

import org.pcap4j.core.BpfProgram.BpfCompileMode;
import org.pcap4j.core.NotOpenException;
import org.pcap4j.core.PcapHandle;
import org.pcap4j.core.PcapNetworkInterface;
import org.pcap4j.core.PcapNetworkInterface.PromiscuousMode;
import org.pcap4j.packet.Packet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PacketCaptureThread extends Thread {

    private static final Logger logger = LoggerFactory.getLogger(PacketCaptureThread.class);
    private PcapNetworkInterface device;
    private PacketCaptureListener listener;
    private int timeout;
    private String filterExpression;
    private PcapHandle handle;
    private ClassLoader classLoader = null;

    public PacketCaptureThread(PcapNetworkInterface device, int timeout, PacketCaptureListener listener,
            String filterExpression, ClassLoader classLoader) {
        this.device = device;
        this.listener = listener;
        this.timeout = timeout;
        this.filterExpression = filterExpression;
        this.classLoader = classLoader;
        init();
    }

    private synchronized void init() {
        try {
            handle = device.openLive(65536, PromiscuousMode.PROMISCUOUS, timeout);
            if (!"".equals(filterExpression)) {
                handle.setFilter(filterExpression, BpfCompileMode.OPTIMIZE);
            }
        } catch (Exception e) {
            logger.error("init error", e);
            breakLoop();
        }
    }

    public static void executeWithTCCLSwitch(final ClassLoader classLoader, final Runnable action) {
        if (classLoader == null) {
            action.run();
        }
        final ClassLoader ccl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(classLoader);
        try {
            action.run();
        } finally {
            Thread.currentThread().setContextClassLoader(ccl);
        }
    }

    @Override
    public void run() {
        while (true) {
            if (handle == null) {
                break;
            }
            executeWithTCCLSwitch(this.classLoader, () -> {
                try {
                    Packet packet = null;
                    packet = handle.getNextPacketEx();
                    if (packet != null && listener != null) {
                        listener.gotPacket(packet);
                    }
                } catch (TimeoutException | EOFException | NotOpenException e) {

                } catch (Exception e) {
                    logger.error("error", e);
                    breakLoop();
                }
            });

        }
    }

    static class CloseRunnable implements Runnable {

        private final PcapHandle handle;

        public CloseRunnable(PcapHandle handle) {
            this.handle = handle;
        }

        public void run() {
            handle.close();
        }
    }

    private Random random = new Random();

    public void breakLoop() {
        if (handle != null) {
            new Thread(new CloseRunnable(handle), "IotProtocolPreventClose-" + random.nextInt()).start();
            this.handle = null;
            this.listener = null;
        }
    }

    public static class Builder {

        private PcapNetworkInterface device;
        private PacketCaptureListener listener;
        private int timeout = 10;
        private String filterExpression = "";
        private ClassLoader classLoader = null;

        Builder() {
        }

        Builder(PcapNetworkInterface device) {
            this.device = device;
        }

        public Builder withDevice(PcapNetworkInterface device) {
            this.device = device;
            return this;
        }

        public Builder withClassLoader(ClassLoader classLoader) {
            this.classLoader = classLoader;
            return this;
        }

        public Builder withTimeout(int timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder withFilterExpression(String filterExpression) {
            this.filterExpression = filterExpression;
            return this;
        }

        public Builder withListener(PacketCaptureListener listener) {
            this.listener = listener;
            return this;
        }

        public PacketCaptureThread build() {
            return new PacketCaptureThread(this.device, this.timeout, this.listener, this.filterExpression,
                    this.classLoader);
        }
    }
}
