package com.yofc.capture;

import org.pcap4j.core.BpfProgram.BpfCompileMode;
import org.pcap4j.core.NotOpenException;
import org.pcap4j.core.PcapHandle;
import org.pcap4j.core.PcapNetworkInterface;
import org.pcap4j.core.PcapNetworkInterface.PromiscuousMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PacketCaptureThread extends Thread {

    private static final Logger logger = LoggerFactory.getLogger(PacketCaptureThread.class);
    private PcapNetworkInterface device;
    private PacketCaptureListener listener;
    private int timeout;
    private String filterExpression;
    private PcapHandle handle;

    public PacketCaptureThread(PcapNetworkInterface device, int timeout, PacketCaptureListener listener,
            String filterExpression) {
        this.device = device;
        this.listener = listener;
        this.timeout = timeout;
        this.filterExpression = filterExpression;
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

    @Override
    public void run() {
        if (handle != null) {
            try {
                handle.loop(-1, listener);
            } catch (InterruptedException e) {
                breakLoop();
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                logger.error("error", e);
                breakLoop();
            } finally {
                handle = null;
            }
        }
    }

    public synchronized void breakLoop() {
        if (handle != null) {
            try {
                try {
                    handle.breakLoop();
                } catch (NotOpenException e) {

                }
                handle.close();

            } finally {
                handle = null;
            }
        }
    }

    public static class Builder {

        private PcapNetworkInterface device;
        private PacketCaptureListener listener;
        private int timeout = 10;
        private String filterExpression = "";

        Builder() {
        }

        Builder(PcapNetworkInterface device) {
            this.device = device;
        }

        public Builder withDevice(PcapNetworkInterface device) {
            this.device = device;
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
            return new PacketCaptureThread(this.device, this.timeout, this.listener, this.filterExpression);
        }
    }
}
