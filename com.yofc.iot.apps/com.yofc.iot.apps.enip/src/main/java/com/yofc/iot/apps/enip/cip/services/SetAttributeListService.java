package com.yofc.iot.apps.enip.cip.services;

import com.yofc.iot.apps.enip.cip.CipResponseException;
import com.yofc.iot.apps.enip.cip.epath.EPath.PaddedEPath;
import com.yofc.iot.apps.enip.cip.structs.AttributeRequest;
import com.yofc.iot.apps.enip.cip.structs.AttributeResponse;
import com.yofc.iot.apps.enip.cip.structs.MessageRouterRequest;
import com.yofc.iot.apps.enip.cip.structs.MessageRouterResponse;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.util.function.Function;

public class SetAttributeListService implements CipService<AttributeResponse[]> {

  public static final int SERVICE_CODE = 0x04;

  private final PaddedEPath requestPath;
  private final AttributeRequest[] attributeRequests;
  private final Function<Integer, ByteBuf> attributeDataDecoder;
  private AttributeResponse[] responseData = null;

  public SetAttributeListService(
      PaddedEPath requestPath,
      AttributeRequest[] attributeRequests,
      Function<Integer, ByteBuf> attributeDataDecoder) {

    this.requestPath = requestPath;
    this.attributeRequests = attributeRequests;
    this.attributeDataDecoder = attributeDataDecoder;
  }

  @Override
  public void encodeRequest(ByteBuf buffer) {
    MessageRouterRequest request =
        new MessageRouterRequest(SERVICE_CODE, requestPath, this::encode);

    MessageRouterRequest.encode(request, buffer);
  }

  @Override
  public AttributeResponse[] decodeResponse(ByteBuf buffer)
      throws CipResponseException, PartialResponseException {
    MessageRouterResponse response = MessageRouterResponse.decode(buffer);
    if (response.getGeneralStatus() == 0x00) {
      AttributeResponse[] data = decode(buffer);
      this.responseData = data;
      return data;
    } else {
      throw new CipResponseException(response.getGeneralStatus(), response.getAdditionalStatus());
    }
  }

  private void encode(ByteBuf buffer) {
    buffer.writeShortLE(attributeRequests.length);

    for (AttributeRequest request : attributeRequests) {
      buffer.writeShortLE(request.getId());
      buffer.writeBytes(request.getData());
    }
  }

  private AttributeResponse[] decode(ByteBuf buffer) {
    int count = buffer.readUnsignedShortLE();

    AttributeResponse[] responses = new AttributeResponse[count];

    for (int i = 0; i < count; i++) {
      int id = buffer.readShortLE();
      int status = buffer.readShortLE();

      ByteBuf data = (status == 0x00) ? attributeDataDecoder.apply(id) : Unpooled.EMPTY_BUFFER;

      responses[i] = new AttributeResponse(id, status, data);
    }

    return responses;
  }

  @Override
  public AttributeResponse[] getResponseData() {
    return responseData;
  }
}
