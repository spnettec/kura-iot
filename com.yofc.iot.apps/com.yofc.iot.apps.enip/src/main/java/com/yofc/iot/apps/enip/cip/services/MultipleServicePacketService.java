package com.yofc.iot.apps.enip.cip.services;

import static java.util.Collections.synchronizedList;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yofc.iot.apps.enip.cip.CipResponseException;
import com.yofc.iot.apps.enip.cip.epath.EPath.PaddedEPath;
import com.yofc.iot.apps.enip.cip.epath.LogicalSegment.ClassId;
import com.yofc.iot.apps.enip.cip.epath.LogicalSegment.InstanceId;
import com.yofc.iot.apps.enip.cip.structs.MessageRouterRequest;
import com.yofc.iot.apps.enip.cip.structs.MessageRouterResponse;

import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCountUtil;

public class MultipleServicePacketService implements CipService<ByteBuf[]> {

    private static Logger logger = LoggerFactory.getLogger(MultipleServicePacketService.class);

    public static final int SERVICE_CODE = 0x0A;

    private static final PaddedEPath MESSAGE_ROUTER_PATH = new PaddedEPath(new ClassId(0x02), new InstanceId(0x01));

    private final List<CipService<?>> currentServices;

    public MultipleServicePacketService(List<CipService<?>> services) {

        this.currentServices = synchronizedList(new ArrayList<>(services));
    }

    @Override
    public void encodeRequest(ByteBuf buffer) {
        MessageRouterRequest request = new MessageRouterRequest(SERVICE_CODE, MESSAGE_ROUTER_PATH, this::encode);

        MessageRouterRequest.encode(request, buffer);
    }

    @Override
    public ByteBuf[] decodeResponse(ByteBuf buffer) throws CipResponseException, PartialResponseException {
        MessageRouterResponse response = MessageRouterResponse.decode(buffer);

        try {
            if (response.getGeneralStatus() == 0x00 || response.getGeneralStatus() == 0x1E) {

                ByteBuf[] serviceData = decode(response.getData());
                ByteBuf[] responseBuf = new ByteBuf[serviceData.length];
                for (int i = 0; i < serviceData.length; i++) {
                    CipService<?> service = currentServices.get(i);
                    try {
                        responseBuf[i] = ((ByteBuf) service.decodeResponse(serviceData[i]));
                    } catch (Exception e) {
                        logger.error("decode service error:", e);
                    }

                    finally {
                        ReferenceCountUtil.release(serviceData[i]);
                    }
                }

                return responseBuf;

            } else {
                throw new CipResponseException(response.getGeneralStatus(), response.getAdditionalStatus());
            }
        } finally {
            ReferenceCountUtil.release(response.getData());
        }
    }

    private void encode(ByteBuf buffer) {
        int serviceCount = currentServices.size();

        buffer.writeShortLE(serviceCount);

        int[] offsets = new int[serviceCount];
        int offsetsStartIndex = buffer.writerIndex();
        buffer.writeZero(serviceCount * 2);

        for (int i = 0; i < serviceCount; i++) {
            offsets[i] = buffer.writerIndex() - offsetsStartIndex + 2;
            currentServices.get(i).encodeRequest(buffer);
        }

        buffer.markWriterIndex();
        buffer.writerIndex(offsetsStartIndex);
        for (int offset : offsets) {
            buffer.writeShortLE(offset);
        }
        buffer.resetWriterIndex();
    }

    private ByteBuf[] decode(ByteBuf buffer) {
        int dataStartIndex = buffer.readerIndex();
        int serviceCount = buffer.readUnsignedShortLE();

        int[] offsets = new int[serviceCount];
        for (int i = 0; i < serviceCount; i++) {
            offsets[i] = buffer.readUnsignedShortLE();
        }

        ByteBuf[] serviceData = new ByteBuf[serviceCount];
        for (int i = 0; i < serviceCount; i++) {
            int offset = offsets[i];

            int length = (i + 1 < serviceCount) ? offsets[i + 1] - offset : buffer.readableBytes();

            serviceData[i] = buffer.slice(dataStartIndex + offsets[i], length).retain();

            buffer.skipBytes(length);
        }

        return serviceData;
    }

}
