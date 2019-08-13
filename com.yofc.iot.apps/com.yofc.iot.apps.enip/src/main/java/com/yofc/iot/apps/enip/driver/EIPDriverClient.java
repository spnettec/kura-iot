package com.yofc.iot.apps.enip.driver;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

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
import com.yofc.vertx.osgi.api.Utils;

import io.netty.buffer.ByteBuf;

public class EIPDriverClient {

    private static final Logger logger = LoggerFactory.getLogger(EIPDriverClient.class);
    private CipConnectionPool pool;
    private CipClient client;
    private final String address;
    private final int slot;
    private final int port;
    private long timeoutMs;

    public EIPDriverClient(final String address, final int slot) {
        this.address = address;
        this.slot = slot;
        this.port = 44818;
    }

    public EIPDriverClient(final String address, final int slot, final int port) {
        this.address = address;
        this.slot = slot;
        this.port = port;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
        if (client != null)
            client.getConfig().setTimeout(Duration.ofMillis(timeoutMs));
    }

    public boolean isConnected() {
        return (client != null && pool != null);
    }

    public void connect() {
        EtherNetIpClientConfig config = EtherNetIpClientConfig.builder(this.address).setSerialNumber(0x00)
                .setPort(this.port).setVendorId(0x00).setTimeout(Duration.ofMillis(this.timeoutMs)).build();

        // backplane, slot 0
        EPath.PaddedEPath connectionPath = new EPath.PaddedEPath(new PortSegment(1, new byte[] { (byte) this.slot }));

        client = new CipClient(config, connectionPath);
        try {
            client.connect().get();
        } catch (InterruptedException | ExecutionException e) {
            logger.error("connection failure", e);
            Thread.currentThread().interrupt();
            client = null;
            throw new RuntimeException(e);
        }

        pool = new CipConnectionPool(2, client, connectionPath, 500);
    }

    public void close() {
        pool.close();
        client.disconnect();
        pool = null;
        client = null;
    }

    public CompletableFuture<CIPData[]> readTags(int maxNumberOfRequestsPerGroup, String... tags) {
        int currentGroupByteCount = 0;
        List<CipService<?>> groupMessages = new ArrayList<>();
        List<CipService<ByteBuf>> mgroupMessages = new ArrayList<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (String tag : tags) {
            ReadTagFragmentedService service = new ReadTagFragmentedService(tag);
            mgroupMessages.add(service);
            int messageSize = service.getRequestSize();
            if (currentGroupByteCount + messageSize > 450 || groupMessages.size() == maxNumberOfRequestsPerGroup) {
                MultipleServicePacketService mservice = new MultipleServicePacketService(groupMessages);
                CompletableFuture<Void> future = executeMultiMessages(mservice);
                futures.add(future);
                currentGroupByteCount = 0;
                groupMessages.clear();
            }
            currentGroupByteCount += messageSize;
            groupMessages.add(service);
        }
        if (!groupMessages.isEmpty()) {
            MultipleServicePacketService mservice = new MultipleServicePacketService(groupMessages);
            CompletableFuture<Void> future = executeMultiMessages(mservice);
            futures.add(future);
        }
        CompletableFuture<CIPData[]> completFuture = new CompletableFuture<>();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()])).whenComplete((action, ex) -> {
            if (ex != null)
                completFuture.completeExceptionally(ex);
            else {
                List<CIPData> cipDatas = new ArrayList<>();
                for (CipService<ByteBuf> msg : mgroupMessages) {
                    ByteBuf data = msg.getResponseData();
                    if (data != null) {
                        try {
                            final CIPData.Type type = CIPData.Type.forCode(data.readShortLE());
                            CIPData cipdata = new CIPData(type, data);
                            cipDatas.add(cipdata);
                        } catch (Exception e) {
                            completFuture.completeExceptionally(e);
                        }
                    } else
                        cipDatas.add(null);
                }
                completFuture.complete(cipDatas.toArray(new CIPData[cipDatas.size()]));
            }

        });
        return completFuture;
    }

    private CompletableFuture<Void> executeMultiMessages(MultipleServicePacketService service) {
        CompletableFuture<Void> completFuture = new CompletableFuture<>();
        pool.acquire().whenComplete(Utils.BiConsumerWrapper((connection, ex) -> {
            if (connection != null) {
                CompletableFuture<ByteBuf[]> f = client.invokeConnected(connection.getO2tConnectionId(), service);

                f.whenComplete(Utils.BiConsumerWrapper((data, ex2) -> {
                    if (data != null) {
                        completFuture.complete(null);
                    } else {
                        completFuture.completeExceptionally(ex2);
                    }
                    pool.release(connection);
                }));
            } else {
                completFuture.completeExceptionally(ex);
            }
        }));
        return completFuture;
    }

    public CompletableFuture<CIPData[]> readTags(String... tags) {
        return readTags(30, tags);
    }

    public CompletableFuture<CIPData> readTag(String tag) {
        CompletableFuture<CIPData> completFuture = new CompletableFuture<>();
        ReadTagService service = new ReadTagService(tag);
        pool.acquire().whenComplete(Utils.BiConsumerWrapper((connection, ex) -> {
            if (connection != null) {
                CompletableFuture<ByteBuf> f = client.invokeConnected(connection.getO2tConnectionId(), service);

                f.whenComplete(Utils.BiConsumerWrapper((data, ex2) -> {
                    if (data != null) {
                        try {
                            final CIPData.Type type = CIPData.Type.forCode(data.readShortLE());
                            CIPData cipdata = new CIPData(type, data);
                            completFuture.complete(cipdata);
                        } catch (Exception e) {
                            completFuture.completeExceptionally(e);
                        }

                    } else {
                        completFuture.completeExceptionally(ex2);
                    }
                    pool.release(connection);
                }));
            } else {
                completFuture.completeExceptionally(ex);
            }
        }));
        return completFuture;
    }

    public CompletableFuture<Void> writeTags(String[] tags, CIPData[] datas) {
        return writeTags(5, tags, datas);
    }

    public CompletableFuture<Void> writeTags(int maxNumberOfRequestsPerGroup, String[] tags, CIPData[] datas) {
        int currentGroupByteCount = 0;
        List<CipService<?>> groupMessages = new ArrayList<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < tags.length; i++) {
            WriteTagService service = new WriteTagService(tags[i], datas[i]);
            int messageSize = service.getRequestSize();
            if (currentGroupByteCount + messageSize > 450 || groupMessages.size() == maxNumberOfRequestsPerGroup) {
                MultipleServicePacketService mservice = new MultipleServicePacketService(groupMessages);
                CompletableFuture<Void> future = executeMultiMessages(mservice);
                futures.add(future);
                currentGroupByteCount = 0;
                groupMessages.clear();
            }
            currentGroupByteCount += messageSize;
            groupMessages.add(service);
        }
        if (!groupMessages.isEmpty()) {
            MultipleServicePacketService mservice = new MultipleServicePacketService(groupMessages);
            CompletableFuture<Void> future = executeMultiMessages(mservice);
            futures.add(future);
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()]));

    }

    public CompletableFuture<Void> writeTag(String tag, CIPData data) {
        CompletableFuture<Void> completFuture = new CompletableFuture<>();
        WriteTagService service = new WriteTagService(tag, data);
        pool.acquire().whenComplete(Utils.BiConsumerWrapper((connection, ex0) -> {
            if (connection != null) {
                CompletableFuture<Void> f = client.invokeConnected(connection.getO2tConnectionId(), service);
                f.whenComplete(Utils.BiConsumerWrapper((v, ex) -> {
                    if (ex != null) {
                        completFuture.completeExceptionally(ex);
                    } else {
                        completFuture.complete(null);
                    }
                    pool.release(connection);
                }));
            } else {
                completFuture.completeExceptionally(ex0);
            }
        }));
        return completFuture;
    }
}
