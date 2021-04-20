package com.yofc.iot.apps.enip.cip.services;

import com.yofc.iot.apps.enip.cip.CipResponseException;
import com.yofc.iot.apps.enip.cip.epath.EPath.PaddedEPath;
import com.yofc.iot.apps.enip.cip.epath.LogicalSegment.ClassId;
import com.yofc.iot.apps.enip.cip.epath.LogicalSegment.InstanceId;
import com.yofc.iot.apps.enip.cip.structs.CIPData;
import com.yofc.iot.apps.enip.cip.structs.MessageRouterRequest;
import com.yofc.iot.apps.enip.cip.structs.MessageRouterResponse;
import com.yofc.iot.apps.enip.logix.services.ReadTagFragmentedService;
import com.yofc.iot.apps.enip.logix.services.ReadTagService;
import com.yofc.iot.apps.enip.logix.services.WriteTagService;
import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.synchronizedList;

public class MultipleServicePacketService implements CipService<ByteBuffer[]> {

  public static final int SERVICE_CODE = 0x0A;
  private static final Logger logger = LoggerFactory.getLogger(MultipleServicePacketService.class);
  private static final PaddedEPath MESSAGE_ROUTER_PATH =
      new PaddedEPath(new ClassId(0x02), new InstanceId(0x01));

  private final List<CipService<?>> currentServices;
  private ByteBuffer[] responseData = null;

  public MultipleServicePacketService(List<CipService<?>> services) {

    this.currentServices = synchronizedList(new ArrayList<>(services));
    this.responseData = new ByteBuffer[services.size()];
  }

  public static MultipleServicePacketService readFragment(String... tags) {
    List<CipService<?>> services = new ArrayList<>();
    for (String tag : tags) {
      services.add(new ReadTagFragmentedService(tag));
    }
    return new MultipleServicePacketService(services);
  }

  public static MultipleServicePacketService read(String... tags) {
    List<CipService<?>> services = new ArrayList<>();
    for (String tag : tags) {
      services.add(new ReadTagService(tag));
    }
    return new MultipleServicePacketService(services);
  }

  public static MultipleServicePacketService writeTags(String[] tags, CIPData[] data) {
    if (tags.length != data.length) {
      throw new IllegalArgumentException(
          "Got " + tags.length + " tags but " + data.length + " values");
    }
    List<CipService<?>> services = new ArrayList<>();
    for (int i = 0; i < tags.length; i++) {
      services.add(new WriteTagService(tags[i], data[i]));
    }
    return new MultipleServicePacketService(services);
  }

  @Override
  public void encodeRequest(ByteBuf buffer) {
    MessageRouterRequest request =
        new MessageRouterRequest(SERVICE_CODE, MESSAGE_ROUTER_PATH, this::encode);

    MessageRouterRequest.encode(request, buffer);
  }

  @Override
  public ByteBuffer[] decodeResponse(ByteBuf buffer)
      throws CipResponseException, PartialResponseException {
    MessageRouterResponse response = MessageRouterResponse.decode(buffer);

    if (response.getGeneralStatus() == 0x00 || response.getGeneralStatus() == 0x1E) {

      ByteBuf[] serviceData = decode(response.getData());
      Boolean isPartial = false;
      List<CipService<?>> completedServices = new ArrayList<>();
      for (int i = 0; i < serviceData.length; i++) {
        CipService<?> service = currentServices.get(i);
        try {
          responseData[getNullresponseData()] =
              ((ByteBuffer) service.decodeResponse(serviceData[i]));
          completedServices.add(service);
        } catch (PartialResponseException e) {
          isPartial = true;
        } catch (Exception e) {
          completedServices.add(service);
          logger.error("decode service error:", e);
        }
      }
      if (isPartial) {
        for (CipService<?> s : completedServices) {
          currentServices.remove(s);
        }
        throw PartialResponseException.INSTANCE;
      }

      return this.responseData;

    } else {
      throw new CipResponseException(response.getGeneralStatus(), response.getAdditionalStatus());
    }
  }

  private int getNullresponseData() {
    for (int i = 0; i < this.responseData.length; i++) {
      if (responseData[i] == null) {
        return i;
      }
    }
    throw new RuntimeException("Data index error");
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

      serviceData[i] = buffer.slice(dataStartIndex + offsets[i], length);

      buffer.skipBytes(length);
    }

    return serviceData;
  }

  @Override
  public ByteBuffer[] getResponseData() {
    return responseData;
  }
}
