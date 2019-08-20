package com.yofc.iot.apps.enip.logix.services;

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
import io.netty.buffer.Unpooled;

public class ReadTagFragmentedService implements CipService<ByteBuf> {

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
    public ByteBuf decodeResponse(ByteBuf buffer) throws CipResponseException, PartialResponseException {
        MessageRouterResponse response = MessageRouterResponse.decode(buffer);

        int status = response.getGeneralStatus();
        ByteBuf data = response.getData();

        if (status == 0x00 || status == 0x06) {
            if (status == 0x06 && data.readableBytes() == 0) {
                throw PartialResponseException.INSTANCE;
            }

            boolean structured = data.getShortLE(data.readerIndex()) == 0x02A0;
            int currentIndex = data.readerIndex();
            data.readerIndex(currentIndex + (structured ? 4 : 2));
            int off = offset.addAndGet(data.readableBytes());
            byte[] bytes = new byte[data.readableBytes()];
            data.readBytes(bytes);
            buffers.add(bytes);
            data.readerIndex(currentIndex);
            if (status == 0x00) {
                synchronized (buffers) {
                    try {
                        ByteBuf buf = Unpooled.buffer().writeBytes(data, structured ? 4 : 2);
                        buffers.stream().forEach(bts -> buf.writeBytes(bts));
                        buf.writerIndex((structured ? 4 : 2) + off);

                        buffers.clear();
                        offset.set(0);
                        this.responseData = buf;
                        return this.responseData;
                    } finally {
                        // ReferenceCountUtil.release(data);
                    }
                }
            } else {
                throw PartialResponseException.INSTANCE;
            }
        } else {
            throw new CipResponseException(status, response.getAdditionalStatus());
        }
    }

    private ByteBuf responseData = null;

    @Override
    public ByteBuf getResponseData() {
        return responseData;
    }

}
