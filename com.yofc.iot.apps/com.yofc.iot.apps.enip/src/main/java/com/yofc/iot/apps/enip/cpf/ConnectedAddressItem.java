package com.yofc.iot.apps.enip.cpf;

import io.netty.buffer.ByteBuf;

/**
 * This address item shall be used when the encapsulated protocol is connection-oriented. The data
 * shall contain a connection identifier.
 */
public final class ConnectedAddressItem extends CpfItem {

  public static final int TYPE_ID = 0xA1;
  private static final int ITEM_LENGTH = 4;
  private final int connectionId;

  /**
   * @param connectionId the connection identifier, exchanged in the Forward Open service of the
   *     Connection Manager.
   */
  public ConnectedAddressItem(int connectionId) {
    super(TYPE_ID);

    this.connectionId = connectionId;
  }

  public static ByteBuf encode(ConnectedAddressItem item, ByteBuf buffer) {
    buffer.writeShortLE(item.getTypeId());
    buffer.writeShortLE(ITEM_LENGTH);
    buffer.writeIntLE(item.getConnectionId());

    return buffer;
  }

  public static ConnectedAddressItem decode(ByteBuf buffer) {
    int typeId = buffer.readUnsignedShortLE();
    int length = buffer.readUnsignedShortLE();
    int connectionId = buffer.readIntLE();

    assert (typeId == TYPE_ID);
    assert (length == ITEM_LENGTH);

    return new ConnectedAddressItem(connectionId);
  }

  public int getConnectionId() {
    return connectionId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ConnectedAddressItem that = (ConnectedAddressItem) o;

    return connectionId == that.connectionId;
  }

  @Override
  public int hashCode() {
    return connectionId;
  }
}
