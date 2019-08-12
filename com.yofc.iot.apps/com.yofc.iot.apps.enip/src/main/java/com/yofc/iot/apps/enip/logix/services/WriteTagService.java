package com.yofc.iot.apps.enip.logix.services;

import com.yofc.iot.apps.enip.cip.CipResponseException;
import com.yofc.iot.apps.enip.cip.epath.DataSegment;
import com.yofc.iot.apps.enip.cip.epath.EPath;
import com.yofc.iot.apps.enip.cip.epath.EPath.PaddedEPath;
import com.yofc.iot.apps.enip.cip.services.CipService;
import com.yofc.iot.apps.enip.cip.structs.CIPData;
import com.yofc.iot.apps.enip.cip.structs.CIPData.Type;
import com.yofc.iot.apps.enip.cip.structs.MessageRouterRequest;
import com.yofc.iot.apps.enip.cip.structs.MessageRouterResponse;

import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCountUtil;

public class WriteTagService implements CipService<Void> {

    public static final int SERVICE_CODE = 0x4D;

    private final PaddedEPath requestPath;
    private final boolean structured;
    private final int tagType;
    private final int elementCount;
    private final ByteBuf data;

    public WriteTagService(PaddedEPath requestPath, boolean structured, int tagType, ByteBuf data) {
        this(requestPath, structured, tagType, 1, data);
    }

    public WriteTagService(String tagName, CIPData cipData) {
        this.requestPath = new EPath.PaddedEPath(new DataSegment.AnsiDataSegment(tagName));
        if (cipData.getType() == Type.STRUCT || cipData.getType() == Type.STRUCT_STRING) {
            this.structured = true;
            this.tagType = Type.STRUCT_STRING.getCode();
        } else {
            this.structured = false;
            this.tagType = cipData.getType().getCode();
        }
        this.elementCount = 1;
        this.data = cipData.getData();
        if (this.structured)
            this.data.readerIndex(this.data.readerIndex() + 6);
    }

    public WriteTagService(PaddedEPath requestPath, boolean structured, int tagType, int elementCount, ByteBuf data) {

        this.requestPath = requestPath;
        this.structured = structured;
        this.tagType = tagType;
        this.elementCount = elementCount;
        this.data = data;
    }

    @Override
    public void encodeRequest(ByteBuf buffer) {
        MessageRouterRequest request = new MessageRouterRequest(SERVICE_CODE, requestPath, this::encode);

        MessageRouterRequest.encode(request, buffer);
    }

    @Override
    public Void decodeResponse(ByteBuf buffer) throws CipResponseException, PartialResponseException {
        MessageRouterResponse response = MessageRouterResponse.decode(buffer);

        int generalStatus = response.getGeneralStatus();

        try {
            if (generalStatus == 0x00) {
                return null;
            } else {
                throw new CipResponseException(generalStatus, response.getAdditionalStatus());
            }
        } finally {
            ReferenceCountUtil.release(response.getData());
        }
    }

    private void encode(ByteBuf buffer) {
        if (structured) {
            buffer.writeByte(0xA0).writeByte(0x02);
        }
        buffer.writeShortLE(tagType);
        buffer.writeShortLE(elementCount);
        if (structured) {
            int length = data.readableBytes();
            int offset = data.writerIndex() + 84 - (length > 84 ? 84 : length);
            buffer.writeIntLE(length);
            data.writerIndex(offset);
        }
        buffer.writeBytes(data);
    }

}
