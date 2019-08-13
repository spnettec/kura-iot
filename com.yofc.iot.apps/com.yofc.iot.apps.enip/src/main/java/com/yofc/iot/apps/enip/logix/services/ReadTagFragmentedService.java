package com.yofc.iot.apps.enip.logix.services;

import java.util.ArrayList;
import java.util.Collections;
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
import io.netty.util.ReferenceCountUtil;

public class ReadTagFragmentedService implements CipService<ByteBuf> {

    public static final int SERVICE_CODE = 0x52;

    private final Consumer<ByteBuf> dataEncoder = this::encode;

    private final List<ByteBuf> buffers = Collections.synchronizedList(new ArrayList<>());
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

    public int getRequestSize() {
        ByteBuf buf = Unpooled.buffer(600);
        try {
            this.encodeRequest(buf);
            return buf.writerIndex();
        } finally {
            ReferenceCountUtil.release(buf);
        }
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

        try {
            if (status == 0x00 || status == 0x06) {
                if (status == 0x06 && data.readableBytes() == 0) {
                    throw PartialResponseException.INSTANCE;
                }

                boolean structured = data.getShort(data.readerIndex()) == 0x02A0;
                ByteBuf header = structured ? data.readSlice(4) : data.readSlice(2);
                ByteBuf fragment = data.slice().retain();

                buffers.add(fragment);
                int off = offset.addAndGet(fragment.readableBytes());

                if (status == 0x00) {
                    synchronized (buffers) {
                        ByteBuf composite = Unpooled.compositeBuffer().addComponent(header.retain())
                                .addComponents(buffers).writerIndex(header.readableBytes() + off);

                        // Clean up so this service can be re-used...
                        buffers.clear();
                        offset = new AtomicInteger(0);

                        return composite;
                    }
                } else {
                    throw PartialResponseException.INSTANCE;
                }
            } else {
                throw new CipResponseException(status, response.getAdditionalStatus());
            }
        } finally {
            ReferenceCountUtil.release(data);
        }
    }

}
