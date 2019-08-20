package com.yofc.iot.apps.enip.cip.services;

import com.yofc.iot.apps.enip.cip.CipResponseException;
import com.yofc.iot.apps.enip.cip.epath.EPath.PaddedEPath;
import com.yofc.iot.apps.enip.cip.epath.LogicalSegment.ClassId;
import com.yofc.iot.apps.enip.cip.epath.LogicalSegment.InstanceId;
import com.yofc.iot.apps.enip.cip.structs.LargeForwardOpenRequest;
import com.yofc.iot.apps.enip.cip.structs.LargeForwardOpenResponse;
import com.yofc.iot.apps.enip.cip.structs.MessageRouterRequest;
import com.yofc.iot.apps.enip.cip.structs.MessageRouterResponse;

import io.netty.buffer.ByteBuf;

public class LargeForwardOpenService implements CipService<LargeForwardOpenResponse> {

    public static final int SERVICE_CODE = 0x5B;

    private static final PaddedEPath CONNECTION_MANAGER_PATH = new PaddedEPath(new ClassId(0x06), new InstanceId(0x01));

    private final LargeForwardOpenRequest request;

    public LargeForwardOpenService(LargeForwardOpenRequest request) {
        this.request = request;
    }

    @Override
    public void encodeRequest(ByteBuf buffer) {
        MessageRouterRequest mrr = new MessageRouterRequest(SERVICE_CODE, CONNECTION_MANAGER_PATH, this::encode);

        MessageRouterRequest.encode(mrr, buffer);
    }

    @Override
    public LargeForwardOpenResponse decodeResponse(ByteBuf buffer)
            throws CipResponseException, PartialResponseException {
        MessageRouterResponse mResponse = MessageRouterResponse.decode(buffer);

        int generalStatus = mResponse.getGeneralStatus();

        if (generalStatus == 0x00) {
            this.responseData = LargeForwardOpenResponse.decode(mResponse.getData());
            return this.responseData;
        } else {
            throw new CipResponseException(generalStatus, mResponse.getAdditionalStatus());
        }
    }

    private void encode(ByteBuf buffer) {
        LargeForwardOpenRequest.encode(request, buffer);
    }

    private LargeForwardOpenResponse responseData = null;

    @Override
    public LargeForwardOpenResponse getResponseData() {
        return responseData;
    }
}
