package com.yofc.iot.apps.enip.driver;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yofc.iot.apps.enip.EtherNetIpClientConfig;
import com.yofc.iot.apps.enip.cip.CipClient;
import com.yofc.iot.apps.enip.cip.CipConnectionPool;
import com.yofc.iot.apps.enip.cip.epath.EPath;
import com.yofc.iot.apps.enip.cip.epath.PortSegment;
import com.yofc.iot.apps.enip.cip.services.MultipleServicePacketService;
import com.yofc.iot.apps.enip.cip.structs.CIPData;
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

    public boolean isConnected() {
        return (client != null && pool != null);
    }

    public boolean connect() {
        EtherNetIpClientConfig config = EtherNetIpClientConfig.builder(this.address).setSerialNumber(0x00)
                .setPort(this.port).setVendorId(0x00).setTimeout(Duration.ofSeconds(500)).build();

        // backplane, slot 0
        EPath.PaddedEPath connectionPath = new EPath.PaddedEPath(new PortSegment(1, new byte[] { (byte) this.slot }));

        client = new CipClient(config, connectionPath);
        try {
            client.connect().get();
        } catch (InterruptedException | ExecutionException e) {
            logger.error("connection failure", e);
            Thread.currentThread().interrupt();
            client.disconnect();
            client = null;
            return false;
        }

        pool = new CipConnectionPool(2, client, connectionPath, 500);
        return true;
    }

    public void close() {
        pool.close();
        client.disconnect();
        pool = null;
        client = null;
    }

    public CompletableFuture<CIPData[]> readTags(int maxNumberOfRequestsPerGroup, String... tags) {
        CompletableFuture<CIPData[]> completFuture = new CompletableFuture<>();
        MultipleServicePacketService services = MultipleServicePacketService.readFragment(tags);
        pool.acquire().whenComplete(Utils.BiConsumerWrapper((connection, ex) -> {
            if (connection != null) {
                CompletableFuture<ByteBuf[]> f = client.invokeConnected(connection.getO2tConnectionId(), services);

                f.whenComplete(Utils.BiConsumerWrapper((data, ex2) -> {
                    if (data != null) {
                        CIPData[] cipData = new CIPData[data.length];
                        for (int i = 0; i < data.length; i++) {
                            ByteBuf d = data[i];
                            if (d != null) {
                                try {
                                    final CIPData.Type type = CIPData.Type.forCode(d.readShortLE());
                                    CIPData cipdata = new CIPData(type, d);
                                    cipData[i] = cipdata;
                                } catch (Exception e) {
                                    completFuture.completeExceptionally(e);
                                }
                            }
                        }
                        completFuture.complete(cipData);
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
        return readTags(5, tags);
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
        CompletableFuture<Void> completFuture = new CompletableFuture<>();
        MultipleServicePacketService services = MultipleServicePacketService.writeTags(tags, datas);
        pool.acquire().whenComplete(Utils.BiConsumerWrapper((connection, ex0) -> {
            if (connection != null) {
                CompletableFuture<ByteBuf[]> f = client.invokeConnected(connection.getO2tConnectionId(), services);

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
