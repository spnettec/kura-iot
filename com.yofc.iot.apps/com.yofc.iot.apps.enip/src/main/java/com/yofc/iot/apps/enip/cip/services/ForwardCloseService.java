package com.yofc.iot.apps.enip.cip.services;

import com.yofc.iot.apps.enip.cip.CipResponseException;
import com.yofc.iot.apps.enip.cip.epath.EPath.PaddedEPath;
import com.yofc.iot.apps.enip.cip.epath.LogicalSegment.ClassId;
import com.yofc.iot.apps.enip.cip.epath.LogicalSegment.InstanceId;
import com.yofc.iot.apps.enip.cip.structs.ForwardCloseRequest;
import com.yofc.iot.apps.enip.cip.structs.ForwardCloseResponse;
import com.yofc.iot.apps.enip.cip.structs.MessageRouterRequest;
import com.yofc.iot.apps.enip.cip.structs.MessageRouterResponse;

import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCountUtil;

public class ForwardCloseService implements CipService<ForwardCloseResponse> {

    public static final int SERVICE_CODE = 0x4E;

    private static final PaddedEPath CONNECTION_MANAGER_PATH = new PaddedEPath(new ClassId(0x06), new InstanceId(0x01));

    private final ForwardCloseRequest request;

    public ForwardCloseService(ForwardCloseRequest request) {
        this.request = request;
    }

    @Override
    public void encodeRequest(ByteBuf buffer) {
        MessageRouterRequest mrr = new MessageRouterRequest(SERVICE_CODE, CONNECTION_MANAGER_PATH,
                b -> ForwardCloseRequest.encode(request, b));

        MessageRouterRequest.encode(mrr, buffer);
    }

    @Override
    public ForwardCloseResponse decodeResponse(ByteBuf buffer) throws CipResponseException, PartialResponseException {
        MessageRouterResponse mResponse = MessageRouterResponse.decode(buffer);

        int generalStatus = mResponse.getGeneralStatus();

        try {
            if (generalStatus == 0x00) {
                this.responseData = ForwardCloseResponse.decode(mResponse.getData());
                return this.responseData;
            } else {
                throw new CipResponseException(generalStatus, mResponse.getAdditionalStatus());
            }
        } finally {
            ReferenceCountUtil.release(buffer);
        }
    }

    private ForwardCloseResponse responseData = null;

    @Override
    public ForwardCloseResponse getResponseData() {
        return responseData;
    }

}
