package com.yofc.iot.apps.enip.cpf;

import io.netty.buffer.ByteBuf;

import java.util.Arrays;

public final class CpfPacket {

  private final CpfItem[] items;

  public CpfPacket(CpfItem... items) {
    this.items = items;
  }

  public static ByteBuf encode(CpfPacket packet, ByteBuf buffer) {
    buffer.writeShortLE(packet.getItems().length);

    for (CpfItem item : packet.getItems()) {
      CpfItem.encode(item, buffer);
    }

    return buffer;
  }

  public static CpfPacket decode(ByteBuf buffer) {
    int itemCount = buffer.readUnsignedShortLE();
    CpfItem[] items = new CpfItem[itemCount];

    for (int i = 0; i < itemCount; i++) {
      items[i] = CpfItem.decode(buffer);
    }

    return new CpfPacket(items);
  }

  public CpfItem[] getItems() {
    return items;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    CpfPacket cpfPacket = (CpfPacket) o;

    return Arrays.equals(items, cpfPacket.items);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(items);
  }
}
