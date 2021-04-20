package com.yofc.iot.apps.enip.logix.services;

import com.yofc.iot.apps.enip.cip.CipResponseException;
import com.yofc.iot.apps.enip.cip.epath.DataSegment;
import com.yofc.iot.apps.enip.cip.epath.EPath;
import com.yofc.iot.apps.enip.cip.epath.EPath.PaddedEPath;
import com.yofc.iot.apps.enip.cip.services.CipService;
import com.yofc.iot.apps.enip.cip.structs.MessageRouterRequest;
import com.yofc.iot.apps.enip.cip.structs.MessageRouterResponse;
import io.netty.buffer.ByteBuf;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class ReadTagService implements CipService<ByteBuffer> {

  public static final int SERVICE_CODE = 0x4C;
  private final List<byte[]> buffers = new ArrayList<>();
  private final AtomicInteger offset = new AtomicInteger(0);
  private final PaddedEPath requestPath;
  private final int elementCount;
  private final Consumer<ByteBuf> dataEncoder = this::encode;
  private ByteBuffer responseData = null;

  /**
   * Create a ReadTagService requesting 1 element at {@code requestPath}.
   *
   * @param requestPath the path to the tag to read.
   */
  public ReadTagService(PaddedEPath requestPath) {
    this(requestPath, 1);
  }

  public ReadTagService(String tagName) {
    this(tagName, 1);
  }

  public ReadTagService(String tagName, int elementCount) {
    PaddedEPath rp = new EPath.PaddedEPath(new DataSegment.AnsiDataSegment(tagName));
    this.requestPath = rp;
    this.elementCount = elementCount;
  }

  /**
   * Create a ReadTagService requesting {@code elementCount} elements at {@code requestPath}.
   *
   * @param requestPath the path to the tag to read.
   * @param elementCount the number of elements to request.
   */
  public ReadTagService(PaddedEPath requestPath, int elementCount) {
    this.requestPath = requestPath;
    this.elementCount = elementCount;
  }

  @Override
  public void encodeRequest(ByteBuf buffer) {
    MessageRouterRequest request = new MessageRouterRequest(SERVICE_CODE, requestPath, dataEncoder);

    MessageRouterRequest.encode(request, buffer);
  }

  @Override
  public ByteBuffer decodeResponse(ByteBuf buffer)
      throws PartialResponseException, CipResponseException {
    MessageRouterResponse response = MessageRouterResponse.decode(buffer);

    int status = response.getGeneralStatus();
    ByteBuf data = response.getData();

    if (status == 0x00 || status == 0x06) {
      if (status == 0x06 && data.readableBytes() == 0) {
        throw PartialResponseException.INSTANCE;
      }
      short code = data.readShortLE();
      Short subCode = null;
      if (code == 0x02A0) {
        subCode = data.readShortLE();
      }
      int dataLength = data.readableBytes();
      offset.addAndGet(dataLength);
      byte[] bytes = new byte[dataLength];
      data.readBytes(bytes);
      buffers.add(bytes);
      if (status == 0x00) {
        synchronized (buffers) {
          int allCount = buffers.stream().mapToInt(bt -> bt.length).sum() + 2;
          allCount += subCode == null ? 0 : 2;
          ByteBuffer byteBuffer = ByteBuffer.allocate(allCount);
          byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
          byteBuffer.putShort(code);
          if (subCode != null) {
            byteBuffer.putShort(subCode);
          }
          buffers.stream().forEachOrdered(byteBuffer::put);
          buffers.clear();
          offset.set(0);
          byteBuffer.flip();
          this.responseData = byteBuffer;
          return this.responseData;
        }
      } else {
        throw PartialResponseException.INSTANCE;
      }

    } else {
      throw new CipResponseException(status, response.getAdditionalStatus());
    }
  }

  private void encode(ByteBuf buffer) {
    buffer.writeShortLE(elementCount);
    buffer.writeIntLE(offset.get());
  }

  @Override
  public ByteBuffer getResponseData() {
    return responseData;
  }
}
