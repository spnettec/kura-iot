package com.yofc.iot.apps.enip.driver;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yofc.iot.apps.enip.EtherNetIpClientConfig;
import com.yofc.iot.apps.enip.cip.CipClient;
import com.yofc.iot.apps.enip.cip.CipConnectionPool;
import com.yofc.iot.apps.enip.cip.epath.EPath;
import com.yofc.iot.apps.enip.cip.epath.PortSegment;
import com.yofc.iot.apps.enip.cip.services.CipService;
import com.yofc.iot.apps.enip.cip.services.MultipleServicePacketService;
import com.yofc.iot.apps.enip.cip.structs.CIPData;
import com.yofc.iot.apps.enip.logix.services.ReadTagFragmentedService;
import com.yofc.iot.apps.enip.logix.services.ReadTagService;
import com.yofc.iot.apps.enip.logix.services.WriteTagService;
import com.yofc.netty.fsm.NotConnectedException;
import com.yofc.vertx.osgi.api.Utils;

public class EIPDriverClient {

    private static final Logger logger = LoggerFactory.getLogger(EIPDriverClient.class);
    private CipConnectionPool pool;
    private CipClient client;
    private final String address;
    private final int slot;
    private final int port;
    private long timeoutMs = 2000;
    private final int poolSize;

    public EIPDriverClient(final String address, final int slot) {
        this.address = address;
        this.slot = slot;
        this.port = 44818;
        this.poolSize = 2;
    }

    public EIPDriverClient(final String address, final int slot, final int port) {
        this.address = address;
        this.slot = slot;
        this.port = port;
        this.poolSize = 2;
    }

    public EIPDriverClient(final String address, final int slot, final int port, int poolSize) {
        this.address = address;
        this.slot = slot;
        this.port = port;
        this.poolSize = poolSize;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
        if (client != null)
            client.getConfig().setTimeout(Duration.ofMillis(timeoutMs));
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public boolean isConnected() {
        return (client != null && pool != null && client.getState().equals("Connected"));
    }

    public synchronized void connect() {
        EtherNetIpClientConfig config = EtherNetIpClientConfig.builder(this.address).setSerialNumber(0x00).setLazy(true)
                .setPort(this.port).setVendorId(0x00).setTimeout(Duration.ofMillis(this.timeoutMs)).build();
        EPath.PaddedEPath connectionPath = new EPath.PaddedEPath(new PortSegment(1, new byte[] { (byte) this.slot }));
        if (client != null && !client.getState().equals("Connected")) {
            try {
                client.getConfig().setTimeout(Duration.ofMillis(timeoutMs));
                client.connect().get();
            } catch (InterruptedException | ExecutionException e) {
                client = null;
                throw new RuntimeException(e);
            } finally {
                if (pool != null)
                    pool.close();
                pool = null;
            }
        } else {
            client = new CipClient(config, connectionPath);
            try {
                client.connect().get();
            } catch (InterruptedException | ExecutionException e) {
                client = null;
                throw new RuntimeException(e);
            }
        }

        pool = new CipConnectionPool(this.poolSize, client, connectionPath, 500);
    }

    public synchronized void close() {
        pool.close();
        try {
            client.disconnect().get(this.timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
        pool = null;
        client = null;
    }

    public CompletableFuture<CIPData[]> readTags(int maxNumberOfRequestsPerGroup, boolean connected, String... tags) {
        int elementCounts[] = new int[tags.length];
        for (int i = 0; i < elementCounts.length; i++)
            elementCounts[i] = 1;
        return readTags(maxNumberOfRequestsPerGroup, connected, tags, elementCounts);
    }

    public CompletableFuture<CIPData[]> readTags(int maxNumberOfRequestsPerGroup, boolean connected, String tags[],
            int elementCounts[]) {
        CompletableFuture<CIPData[]> completFuture = new CompletableFuture<>();
        if (tags.length != elementCounts.length) {
            completFuture.completeExceptionally(new RuntimeException("paramater error"));
            return completFuture;
        }
        int currentGroupByteCount = 0;
        List<CipService<?>> groupMessages = new ArrayList<>();
        List<CipService<ByteBuffer>> mgroupMessages = new ArrayList<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < tags.length; i++) {
            String tag = tags[i];
            int elementCount = elementCounts[i];
            ReadTagFragmentedService service = new ReadTagFragmentedService(tag, elementCount);
            mgroupMessages.add(service);
            int messageSize = service.getRequestSize();
            if (currentGroupByteCount + messageSize > 450 || groupMessages.size() == maxNumberOfRequestsPerGroup) {
                MultipleServicePacketService mservice = new MultipleServicePacketService(groupMessages);
                CompletableFuture<Void> future;
                if (connected)
                    future = executeMultiMessages(mservice);
                else
                    future = client.invokeUnconnected(mservice).thenApply(b -> null);
                futures.add(future);
                currentGroupByteCount = 0;
                groupMessages.clear();
            }
            currentGroupByteCount += messageSize;
            groupMessages.add(service);
        }
        if (!groupMessages.isEmpty()) {
            MultipleServicePacketService mservice = new MultipleServicePacketService(groupMessages);
            CompletableFuture<Void> future;
            if (connected)
                future = executeMultiMessages(mservice);
            else
                future = client.invokeUnconnected(mservice).thenApply(b -> null);
            futures.add(future);
        }

        sequenceFuture(futures).whenComplete(Utils.BiConsumerWrapper((action, ex) -> {
            if (ex != null)
                completFuture.completeExceptionally(ex);
            else {
                List<CIPData> cipDatas = new ArrayList<>();
                for (CipService<ByteBuffer> msg : mgroupMessages) {
                    ByteBuffer data = msg.getResponseData();
                    if (data != null) {
                        try {
                            CIPData cipdata = new CIPData(data);
                            cipDatas.add(cipdata);
                        } catch (Exception e) {
                            completFuture.completeExceptionally(e);
                        }
                    } else
                        cipDatas.add(null);
                }
                completFuture.complete(cipDatas.toArray(new CIPData[cipDatas.size()]));
            }

        }));
        return completFuture;
    }

    private CompletableFuture<Void> executeMultiMessages(MultipleServicePacketService service) {
        CompletableFuture<Void> completFuture = new CompletableFuture<>();
        try {
            if (!isConnected())
                connect();
        } catch (Exception e) {
            completFuture.completeExceptionally(e);
            return completFuture;
        }
        pool.acquire().whenComplete(Utils.BiConsumerWrapper((connection, ex) -> {
            if (connection != null) {
                try {
                    CompletableFuture<ByteBuffer[]> f = client.invokeConnected(connection.getO2tConnectionId(),
                            service);

                    f.whenComplete(Utils.BiConsumerWrapper((data, ex2) -> {
                        if (data != null) {
                            completFuture.complete(null);
                        } else {
                            if (ex2 instanceof TimeoutException) {
                                if (EIPDriverClient.this.isConnected()) {
                                    logger.info("first timeout,reconnect");
                                    pool.expired();
                                    client.reConnect();
                                }
                                executeMultiMessagesWithFuture(service, completFuture);
                                logger.info("first timeout excute failer excute again");
                            } else {
                                completFuture.completeExceptionally(ex2);
                            }
                        }

                    }));
                } finally {
                    pool.release(connection);
                }
            } else {
                if (ex instanceof NotConnectedException) {
                    executeMultiMessagesWithFuture(service, completFuture);
                    logger.info("first get connection failer agin");
                } else
                    completFuture.completeExceptionally(ex);
            }
        }));
        return completFuture;
    }

    private void executeMultiMessagesWithFuture(MultipleServicePacketService service,
            CompletableFuture<Void> completFuture) {
        try {
            if (!isConnected())
                connect();
        } catch (Exception e) {
            completFuture.completeExceptionally(e);
            return;
        }
        pool.acquire().whenComplete(Utils.BiConsumerWrapper((connection, ex) -> {
            if (connection != null) {
                try {
                    CompletableFuture<ByteBuffer[]> f = client.invokeConnected(connection.getO2tConnectionId(),
                            service);

                    f.whenComplete(Utils.BiConsumerWrapper((data, ex2) -> {
                        if (data != null) {
                            completFuture.complete(null);
                        } else {
                            completFuture.completeExceptionally(ex2);
                        }
                    }));
                } finally {
                    pool.release(connection);
                }
            } else {
                if (ex instanceof NotConnectedException)
                    if (EIPDriverClient.this.isConnected()) {
                        logger.info("can't connect,reconnect");
                        pool.expired();
                        client.reConnect();
                    }

                completFuture.completeExceptionally(ex);
            }
        }));
    }

    public CompletableFuture<CIPData[]> readTags(boolean connected, String... tags) {
        return readTags(30, connected, tags);
    }

    public CompletableFuture<CIPData> readTag(boolean connected, String tag) {
        return readTag(connected, tag, 1);
    }

    public CompletableFuture<CIPData> readTag(boolean connected, String tag, int elementCount) {
        CompletableFuture<CIPData> completFuture = new CompletableFuture<>();
        ReadTagService service = new ReadTagService(tag, elementCount);
        if (!isConnected())
            connect();
        if (!connected)
            return client.invokeUnconnected(service).thenApply(data -> {
                try {
                    return new CIPData(data);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        pool.acquire().whenComplete(Utils.BiConsumerWrapper((connection, ex) -> {
            if (connection != null) {
                try {
                    CompletableFuture<ByteBuffer> f = client.invokeConnected(connection.getO2tConnectionId(), service);

                    f.whenComplete(Utils.BiConsumerWrapper((data, ex2) -> {
                        if (data != null) {
                            try {
                                CIPData cipdata = new CIPData(data);
                                completFuture.complete(cipdata);
                            } catch (Exception e) {
                                completFuture.completeExceptionally(e);
                            }
                        } else {
                            completFuture.completeExceptionally(ex2);
                        }
                    }));
                } finally {
                    pool.release(connection);
                }
            } else {
                completFuture.completeExceptionally(ex);
            }
        }));
        return completFuture;
    }

    public CompletableFuture<Void> writeTags(boolean connected, String[] tags, CIPData[] datas) {
        return writeTags(5, connected, tags, datas);
    }

    public CompletableFuture<Void> writeTags(int maxNumberOfRequestsPerGroup, boolean connected, String[] tags,
            CIPData[] datas) {
        int currentGroupByteCount = 0;
        List<CipService<?>> groupMessages = new ArrayList<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < tags.length; i++) {
            WriteTagService service = new WriteTagService(tags[i], datas[i]);
            int messageSize = service.getRequestSize();
            if (currentGroupByteCount + messageSize > 450 || groupMessages.size() == maxNumberOfRequestsPerGroup) {
                MultipleServicePacketService mservice = new MultipleServicePacketService(groupMessages);
                CompletableFuture<Void> future;
                if (connected)
                    future = executeMultiMessages(mservice);
                else
                    future = client.invokeUnconnected(mservice).thenApply(b -> null);
                futures.add(future);
                currentGroupByteCount = 0;
                groupMessages.clear();
            }
            currentGroupByteCount += messageSize;
            groupMessages.add(service);
        }
        if (!groupMessages.isEmpty()) {
            MultipleServicePacketService mservice = new MultipleServicePacketService(groupMessages);
            CompletableFuture<Void> future;
            if (connected)
                future = executeMultiMessages(mservice);
            else
                future = client.invokeUnconnected(mservice).thenApply(b -> null);
            futures.add(future);
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()]));

    }

    public CompletableFuture<Void> writeTag(boolean connected, String tag, CIPData data) {
        CompletableFuture<Void> completFuture = new CompletableFuture<>();
        WriteTagService service = new WriteTagService(tag, data);
        if (!isConnected())
            connect();
        if (!connected)
            return client.invokeUnconnected(service);
        pool.acquire().whenComplete(Utils.BiConsumerWrapper((connection, ex0) -> {
            if (connection != null) {
                try {
                    CompletableFuture<Void> f = client.invokeConnected(connection.getO2tConnectionId(), service);
                    f.whenComplete(Utils.BiConsumerWrapper((v, ex) -> {
                        if (ex != null) {
                            completFuture.completeExceptionally(ex);
                        } else {
                            completFuture.complete(null);
                        }
                    }));
                } finally {
                    pool.release(connection);
                }
            } else {
                completFuture.completeExceptionally(ex0);
            }
        }));
        return completFuture;
    }

    private <R> CompletableFuture<List<R>> sequenceFuture(List<CompletableFuture<R>> futures) {
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream().map(this::getValue).collect(Collectors.toList()));
    }

    private <T> T getValue(CompletableFuture<T> future) {
        try {
            return future.get();
        } catch (Exception ex) {
            logger.error("getValue for async result failed", ex);
        }
        return null;
    }
}
