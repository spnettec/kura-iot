package com.yofc.iot.apps.enip.cpf;

import io.netty.buffer.ByteBuf;

import java.nio.ByteBuffer;

public final class ConnectedDataItemResponse extends CpfItem {

  public static final int TYPE_ID = 0xB1;

  private final ByteBuffer data;

  public ConnectedDataItemResponse(ByteBuffer data) {
    super(TYPE_ID);

    this.data = data;
  }

  public static ByteBuf encode(ConnectedDataItemResponse item, ByteBuf buffer) {
    buffer.writeShortLE(item.getTypeId());

    // Length placeholder...
    int lengthStartIndex = buffer.writerIndex();
    buffer.writeShortLE(0);

    // Encode the encapsulated data...
    int dataStartIndex = buffer.writerIndex();
    buffer.writeBytes(item.getData());
    // item.getData().release();

    // Go back and update the length.
    int bytesWritten = buffer.writerIndex() - dataStartIndex;
    buffer.markWriterIndex();
    buffer.writerIndex(lengthStartIndex);
    buffer.writeShortLE(bytesWritten);
    buffer.resetWriterIndex();

    return buffer;
  }

  public static ConnectedDataItemResponse decode(ByteBuf buffer) {
    int typeId = buffer.readUnsignedShortLE();
    int length = buffer.readUnsignedShortLE();

    assert (typeId == TYPE_ID);

    byte[] bytes = new byte[length];
    buffer.readBytes(bytes);

    // ByteBuf data = buffer.readSlice(length).retain();

    return new ConnectedDataItemResponse(ByteBuffer.wrap(bytes));
  }

  public ByteBuffer getData() {
    return data;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ConnectedDataItemResponse that = (ConnectedDataItemResponse) o;

    return data.equals(that.data);
  }

  @Override
  public int hashCode() {
    return data.hashCode();
  }
}
