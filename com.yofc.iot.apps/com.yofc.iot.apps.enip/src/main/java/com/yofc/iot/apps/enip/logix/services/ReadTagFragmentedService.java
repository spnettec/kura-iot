package com.yofc.iot.apps.enip.logix.services;

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
import io.netty.util.ReferenceCountUtil;

public class ReadTagFragmentedService implements CipService<ByteBuf> {

    public static final int SERVICE_CODE = 0x52;

    private final Consumer<ByteBuf> dataEncoder = this::encode;

    private final ByteBuf buffers = Unpooled.buffer();
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
            buffers.writeBytes(data);
            data.readerIndex(currentIndex);
            if (status == 0x00) {
                synchronized (buffers) {
                    try {
                        this.responseData = Unpooled.buffer().writeBytes(data, structured ? 4 : 2).writeBytes(buffers)
                                .writerIndex((structured ? 4 : 2) + off);
                        buffers.clear();
                        offset.set(0);
                        return this.responseData;
                    } finally {
                        ReferenceCountUtil.release(buffers);
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
