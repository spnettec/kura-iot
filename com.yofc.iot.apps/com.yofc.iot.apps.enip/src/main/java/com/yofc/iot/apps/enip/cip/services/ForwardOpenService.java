package com.yofc.iot.apps.enip.cip.services;

import com.yofc.iot.apps.enip.cip.CipResponseException;
import com.yofc.iot.apps.enip.cip.epath.EPath.PaddedEPath;
import com.yofc.iot.apps.enip.cip.epath.LogicalSegment.ClassId;
import com.yofc.iot.apps.enip.cip.epath.LogicalSegment.InstanceId;
import com.yofc.iot.apps.enip.cip.structs.ForwardOpenRequest;
import com.yofc.iot.apps.enip.cip.structs.ForwardOpenResponse;
import com.yofc.iot.apps.enip.cip.structs.MessageRouterRequest;
import com.yofc.iot.apps.enip.cip.structs.MessageRouterResponse;

import io.netty.buffer.ByteBuf;

public class ForwardOpenService implements CipService<ForwardOpenResponse> {

    public static final int SERVICE_CODE = 0x54;

    private static final PaddedEPath CONNECTION_MANAGER_PATH = new PaddedEPath(new ClassId(0x06), new InstanceId(0x01));

    private final ForwardOpenRequest request;

    public ForwardOpenService(ForwardOpenRequest request) {
        this.request = request;
    }

    @Override
    public void encodeRequest(ByteBuf buffer) {
        MessageRouterRequest mrr = new MessageRouterRequest(SERVICE_CODE, CONNECTION_MANAGER_PATH, this::encode);

        MessageRouterRequest.encode(mrr, buffer);
    }

    @Override
    public ForwardOpenResponse decodeResponse(ByteBuf buffer) throws CipResponseException, PartialResponseException {
        MessageRouterResponse mResponse = MessageRouterResponse.decode(buffer);

        int generalStatus = mResponse.getGeneralStatus();

        if (generalStatus == 0x00) {
            this.responseData = ForwardOpenResponse.decode(mResponse.getData());
            return this.responseData;
        } else {
            throw new CipResponseException(generalStatus, mResponse.getAdditionalStatus());
        }
    }

    private void encode(ByteBuf buffer) {
        ForwardOpenRequest.encode(request, buffer);
    }

    private ForwardOpenResponse responseData = null;

    @Override
    public ForwardOpenResponse getResponseData() {
        return responseData;
    }
}
