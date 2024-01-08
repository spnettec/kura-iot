package com.yofc.capture;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.pcap4j.core.BpfProgram.BpfCompileMode;
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
    private ExecutorService executor = Executors.newSingleThreadExecutor();

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
        try {
            if (handle != null) {
                handle.loop(-1, listener, executor);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            breakLoop();
        } catch (Exception e) {
            logger.error("error", e);
            breakLoop();
        }
    }

    static class CloseRunnable implements Runnable {

        private final PcapHandle handle;

        public CloseRunnable(PcapHandle handle) {
            this.handle = handle;
        }

        public void run() {
            if (handle != null) {
                try {
                    handle.breakLoop();
                } catch (Exception ignored) {

                }
                handle.close();
            }
        }
    }

    private Random random = new Random();

    public synchronized void breakLoop() {
        if (handle != null) {
            this.executor.shutdown();
            this.executor = null;
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
