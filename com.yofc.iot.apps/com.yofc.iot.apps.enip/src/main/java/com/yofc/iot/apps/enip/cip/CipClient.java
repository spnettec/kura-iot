package com.yofc.iot.apps.enip.cip;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yofc.iot.apps.enip.EtherNetIpClient;
import com.yofc.iot.apps.enip.EtherNetIpClientConfig;
import com.yofc.iot.apps.enip.cip.epath.EPath;
import com.yofc.iot.apps.enip.cip.services.CipService;
import com.yofc.iot.apps.enip.cip.services.CipServiceInvoker;
import com.yofc.iot.apps.enip.cip.services.UnconnectedSendService;
import com.yofc.iot.apps.enip.commands.SendRRData;
import com.yofc.iot.apps.enip.commands.SendUnitData;
import com.yofc.iot.apps.enip.cpf.ConnectedAddressItem;
import com.yofc.iot.apps.enip.cpf.ConnectedDataItemRequest;
import com.yofc.iot.apps.enip.cpf.ConnectedDataItemResponse;
import com.yofc.iot.apps.enip.cpf.CpfItem;
import com.yofc.iot.apps.enip.cpf.CpfPacket;
import com.yofc.iot.apps.enip.cpf.NullAddressItem;
import com.yofc.iot.apps.enip.cpf.UnconnectedDataItemRequest;
import com.yofc.iot.apps.enip.cpf.UnconnectedDataItemResponse;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.Timeout;

public class CipClient extends EtherNetIpClient implements CipServiceInvoker {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ConnectedDataHandler connectedDataHandler = new ConnectedDataHandler();
    private final List<CpfItemHandler> additionalHandlers = new CopyOnWriteArrayList<>();

    private final Map<Integer, CompletableFuture<ByteBuffer>> pending = new ConcurrentHashMap<>();
    private final Map<Integer, Timeout> timeouts = new ConcurrentHashMap<>();

    private final AtomicInteger sequenceNumber = new AtomicInteger(0);

    private final EPath.PaddedEPath connectionPath;

    public CipClient(EtherNetIpClientConfig config, EPath.PaddedEPath connectionPath) {
        super(config);

        this.connectionPath = connectionPath;
    }

    @Override
    public <T> CompletableFuture<T> invokeConnected(int connectionId, CipService<T> service) {
        CompletableFuture<T> future = new CompletableFuture<>();

        return invokeConnected(connectionId, service, future);
    }

    private <T> CompletableFuture<T> invokeConnected(int connectionId, CipService<T> service,
            CompletableFuture<T> future) {

        sendConnectedData(service::encodeRequest, connectionId).whenComplete((buffer, ex) -> {
            if (buffer != null) {
                ByteBuf buf = null;
                try {
                    buf = Unpooled.wrappedBuffer(buffer.order(ByteOrder.BIG_ENDIAN));
                    T response = service.decodeResponse(buf);

                    future.complete(response);
                } catch (CipService.PartialResponseException e) {
                    invokeConnected(connectionId, service, future);
                } catch (CipResponseException e) {
                    future.completeExceptionally(e);
                } finally {
                    if (buf != null)
                        ReferenceCountUtil.release(buf);
                }
            } else {
                future.completeExceptionally(ex);
            }
        });

        return future;
    }

    @Override
    public <T> CompletableFuture<T> invokeUnconnected(CipService<T> service) {
        return invokeUnconnected(service, 0);
    }

    @Override
    public <T> CompletableFuture<T> invokeUnconnected(CipService<T> service, int maxRetries) {
        UnconnectedSendService<T> uss = new UnconnectedSendService<T>(service, connectionPath,
                getConfig().getTimeout());

        return invoke(uss, maxRetries);
    }

    @Override
    public <T> CompletableFuture<T> invoke(CipService<T> service) {
        return invoke(service, 0);
    }

    @Override
    public <T> CompletableFuture<T> invoke(CipService<T> service, int maxRetries) {
        CompletableFuture<T> future = new CompletableFuture<>();

        return invoke(service, future, 0, maxRetries);
    }

    private <T> CompletableFuture<T> invoke(CipService<T> service, CompletableFuture<T> future, int count,
            int maxRetries) {

        sendUnconnectedData(service::encodeRequest).whenComplete((buffer, ex) -> {
            if (buffer != null) {
                ByteBuf buf = null;
                try {
                    buf = Unpooled.wrappedBuffer(buffer.order(ByteOrder.BIG_ENDIAN));
                    T response = service.decodeResponse(buf);

                    future.complete(response);
                } catch (CipService.PartialResponseException e) {
                    invoke(service, future, count, maxRetries);
                } catch (CipResponseException e) {
                    if (e.getGeneralStatus() == 0x01) {
                        boolean requestTimedOut = Arrays.stream(e.getAdditionalStatus()).anyMatch(i -> i == 0x0204);

                        if (requestTimedOut && count < maxRetries) {
                            logger.debug("Unconnected request timed out; " + "retrying, count={}, max={}", count,
                                    maxRetries);
                            invoke(service, future, count + 1, maxRetries);
                        } else {
                            future.completeExceptionally(e);
                        }
                    } else {
                        future.completeExceptionally(e);
                    }
                } catch (Exception e) {
                    future.completeExceptionally(e);
                } finally {
                    if (buf != null)
                        ReferenceCountUtil.release(buf);
                }
            } else {
                future.completeExceptionally(ex);
            }
        });

        return future;
    }

    public CompletableFuture<ByteBuffer> sendConnectedData(ByteBuf data, int connectionId) {
        return sendConnectedData((buffer) -> buffer.writeBytes(data), connectionId);
    }

    public CompletableFuture<ByteBuffer> sendConnectedData(Consumer<ByteBuf> dataEncoder, int connectionId) {
        CompletableFuture<ByteBuffer> future = new CompletableFuture<>();

        ConnectedAddressItem addressItem = new ConnectedAddressItem(connectionId);

        int sequenceNumber = nextSequenceNumber();

        ConnectedDataItemRequest dataItem = new ConnectedDataItemRequest((b) -> {
            b.writeShortLE(sequenceNumber);
            dataEncoder.accept(b);
        });

        CpfPacket packet = new CpfPacket(addressItem, dataItem);
        SendUnitData command = new SendUnitData(packet);

        Timeout timeout = getConfig().getWheelTimer().newTimeout(tt -> {
            if (tt.isCancelled())
                return;
            CompletableFuture<ByteBuffer> f = pending.remove(sequenceNumber);
            if (f != null) {
                String message = String.format("sequenceNumber=%s timed out waiting %sms for response", sequenceNumber,
                        getConfig().getTimeout().toMillis());
                f.completeExceptionally(new TimeoutException(message));
            }
        }, getConfig().getTimeout().toMillis(), TimeUnit.MILLISECONDS);

        pending.put(sequenceNumber, future);
        timeouts.put(sequenceNumber, timeout);

        sendUnitData(command).whenComplete((v, ex) -> {
            // sendUnitData() fails fast if the channel isn't available
            if (ex != null)
                future.completeExceptionally(ex);
        });

        return future;
    }

    public CompletableFuture<ByteBuffer> sendUnconnectedData(ByteBuf data) {
        return sendUnconnectedData((buffer) -> buffer.writeBytes(data));
    }

    public CompletableFuture<ByteBuffer> sendUnconnectedData(Consumer<ByteBuf> dataEncoder) {
        CompletableFuture<ByteBuffer> future = new CompletableFuture<>();

        UnconnectedDataItemRequest dataItem = new UnconnectedDataItemRequest(dataEncoder);
        CpfPacket packet = new CpfPacket(new NullAddressItem(), dataItem);

        sendRRData(new SendRRData(packet)).whenComplete((command, ex) -> {
            if (command != null) {
                CpfItem[] items = command.getPacket().getItems();

                if (items.length == 2 && items[0].getTypeId() == NullAddressItem.TYPE_ID
                        && items[1].getTypeId() == UnconnectedDataItemResponse.TYPE_ID) {

                    ByteBuffer data = ((UnconnectedDataItemResponse) items[1]).getData();

                    future.complete(data);
                } else {
                    future.completeExceptionally(new Exception("received unexpected items"));
                }
            } else {
                future.completeExceptionally(ex);
            }
        });

        return future;
    }

    @Override
    protected void onUnitDataReceived(SendUnitData command) {
        CpfItem[] items = command.getPacket().getItems();

        if (connectedDataHandler.itemsMatch(items)) {
            connectedDataHandler.itemsReceived(items);
        } else {
            for (CpfItemHandler handler : additionalHandlers) {
                if (handler.itemsMatch(items)) {
                    handler.itemsReceived(items);
                    break;
                }
            }
        }
    }

    private int nextSequenceNumber() {
        int num = sequenceNumber.incrementAndGet();
        if (num < 0) {
            sequenceNumber.set(1);
            num = 1;
        }
        return num;
    }

    private class ConnectedDataHandler implements CpfItemHandler {

        @Override
        public void itemsReceived(CpfItem[] items) {
            // int connectionId = ((ConnectedAddressItem) items[0]).getConnectionId();
            ByteBuffer buffer = ((ConnectedDataItemResponse) items[1]).getData();
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            int sequenceNumber = buffer.getShort();
            buffer.slice();

            Timeout timeout = timeouts.remove(sequenceNumber);
            if (timeout != null)
                timeout.cancel();

            CompletableFuture<ByteBuffer> future = pending.remove(sequenceNumber);

            if (future != null)
                future.complete(buffer);
        }

        @Override
        public boolean itemsMatch(CpfItem[] items) {
            return items.length == 2 && items[0].getTypeId() == ConnectedAddressItem.TYPE_ID
                    && items[1].getTypeId() == ConnectedDataItemResponse.TYPE_ID;
        }

    }

    public static interface CpfItemHandler {

        boolean itemsMatch(CpfItem[] items);

        void itemsReceived(CpfItem[] items);
    }

}
