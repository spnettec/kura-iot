package com.yofc.iot.apps.enip.logix.services;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.yofc.iot.apps.enip.cip.CipResponseException;
import com.yofc.iot.apps.enip.cip.epath.DataSegment;
import com.yofc.iot.apps.enip.cip.epath.EPath;
import com.yofc.iot.apps.enip.cip.epath.EPath.PaddedEPath;
import com.yofc.iot.apps.enip.cip.services.CipService;
import com.yofc.iot.apps.enip.cip.structs.MessageRouterRequest;
import com.yofc.iot.apps.enip.cip.structs.MessageRouterResponse;

import io.netty.buffer.ByteBuf;

public class ReadTagFragmentedService implements CipService<ByteBuffer> {

    public static final int SERVICE_CODE = 0x52;

    private final Consumer<ByteBuf> dataEncoder = this::encode;

    private final List<byte[]> buffers = new ArrayList<>();
    private AtomicInteger offset = new AtomicInteger(0);

    private final PaddedEPath requestPath;
    private final int elementCount;

    public ReadTagFragmentedService(PaddedEPath requestPath) {
        this(requestPath, 1);
    }

    public ReadTagFragmentedService(PaddedEPath requestPath, int elementCount) {
        this.requestPath = requestPath;
        this.elementCount = elementCount;
    }

    public ReadTagFragmentedService(String tagName) {
        this(tagName, 1);
    }

    public ReadTagFragmentedService(String tagName, int elementCount) {
        PaddedEPath rp = new EPath.PaddedEPath(new DataSegment.AnsiDataSegment(tagName));
        this.requestPath = rp;
        this.elementCount = elementCount;
    }

    @Override
    public void encodeRequest(ByteBuf buffer) {
        MessageRouterRequest request = new MessageRouterRequest(SERVICE_CODE, requestPath, dataEncoder);

        MessageRouterRequest.encode(request, buffer);
    }

    private void encode(ByteBuf buffer) {
        buffer.writeShortLE(elementCount);
        buffer.writeIntLE(offset.get());
    }

    @Override
    public ByteBuffer decodeResponse(ByteBuf buffer) throws CipResponseException, PartialResponseException {
        MessageRouterResponse response = MessageRouterResponse.decode(buffer);

        int status = response.getGeneralStatus();
        ByteBuf data = response.getData();

        if (status == 0x00 || status == 0x06) {
            if (status == 0x06 && data.readableBytes() == 0) {
                throw PartialResponseException.INSTANCE;
            }

            boolean structured = data.getShortLE(data.readerIndex()) == 0x02A0;
            int currentIndex = data.readerIndex();
            int headCount = structured ? 4 : 2;
            data.readerIndex(currentIndex + headCount);
            byte[] bytes = new byte[data.readableBytes()];
            data.readBytes(bytes);
            buffers.add(bytes);
            data.readerIndex(currentIndex);
            if (status == 0x00) {
                synchronized (buffers) {
                    byte[] head = new byte[headCount];
                    data.readBytes(head);
                    buffers.add(0, head);
                    int allCount = buffers.stream().mapToInt(bt -> bt.length).sum();
                    ByteBuffer byteBuffer = ByteBuffer.allocate(allCount).order(ByteOrder.LITTLE_ENDIAN);
                    buffers.stream().forEach(byteBuffer::put);
                    buffers.clear();
                    offset.set(0);
                    byteBuffer.flip();
                    this.responseData = byteBuffer;
                    return this.responseData;
                }
            } else {
                throw PartialResponseException.INSTANCE;
            }
        } else {
            throw new CipResponseException(status, response.getAdditionalStatus());
        }
    }

    private ByteBuffer responseData = null;

    @Override
    public ByteBuffer getResponseData() {
        return responseData;
    }

}
