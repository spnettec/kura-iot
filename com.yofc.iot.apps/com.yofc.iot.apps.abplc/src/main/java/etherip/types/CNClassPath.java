/*******************************************************************************
 * Copyright (c) 2012 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package etherip.types;

import io.netty.buffer.ByteBuf;

/**
 * Control Net Path for class, instance, attribute
 *
 * <p>Example (with suitable static import):
 *
 * <p><code>CNPath path = Identity.instance(1).attr(7)</code>
 *
 * @author heyoulin
 */
public class CNClassPath extends CNPath {

  private int class_code;
  private String class_name;
  private int instance = 1, attr = 0;

  public CNClassPath() {}

  protected CNClassPath(final int class_code, final String class_name) {
    this.class_code = class_code;
    this.class_name = class_name;
  }

  public CNClassPath instance(final int instance) {
    this.instance = instance;
    return this;
  }

  public CNPath attr(final int attr) {
    this.attr = attr;
    return this;
  }

  /** @return Path length in words */
  public byte getPathLength(int size) {
    return (byte) (size / 2);
  }

  /** {@inheritDoc} */
  @Override
  public void encode(final ByteBuf buf) {
    int lengthStartIndex = buf.writerIndex();
    buf.writeByte((byte) 0);
    int messageStartIndex = buf.writerIndex();
    buf.writeByte(classSegmentType());
    if (shortClassId()) {
      buf.writeByte((byte) 0); // Padding
      buf.writeShortLE((short) this.class_code);
    } else {
      buf.writeByte((byte) this.class_code);
    }

    buf.writeByte(instanceSegmentType());
    if (shortInstanceId()) {
      buf.writeByte((byte) 0); // Padding
      buf.writeShortLE((short) this.instance);
    } else {
      buf.writeByte((byte) this.instance);
    }

    if (hasAttribute()) {
      buf.writeByte(attributeSegmentType());
      if (shortAttributeId()) {
        buf.writeByte((byte) 0); // Padding
        buf.writeShortLE((short) this.attr);
      } else {
        buf.writeByte((byte) this.attr);
      }
    }
    int bytesWritten = buf.writerIndex() - messageStartIndex;
    buf.markWriterIndex();
    buf.writerIndex(lengthStartIndex);
    buf.writeByte(getPathLength(bytesWritten));
    buf.resetWriterIndex();
  }

  private byte classSegmentType() {
    if (shortClassId()) {
      return 0x21;
    }
    return 0x20;
  }

  private boolean shortClassId() {
    return this.class_code > 0xFF;
  }

  private byte instanceSegmentType() {
    if (shortInstanceId()) {
      return 0x25;
    }
    return 0x24;
  }

  private boolean shortInstanceId() {
    return this.instance > 0xFF;
  }

  private boolean hasAttribute() {
    return this.attr > 0;
  }

  private byte attributeSegmentType() {
    if (shortAttributeId()) {
      return 0x31;
    }
    return 0x30;
  }

  private boolean shortAttributeId() {
    return this.attr > 0xFF;
  }

  @Override
  public String toString() {
    StringBuilder description = new StringBuilder();
    description.append("Path ");
    if (hasAttribute()) {
      description.append("(3 el)");
    } else {
      description.append("(2 el)");
    }
    description.append(" Class(0x").append(String.format("%02X", classSegmentType())).append(" ");
    description.append("0x").append(String.format("%02X", this.class_code)).append(") ");
    description.append(this.class_name);

    description
        .append(", instance(0x")
        .append(String.format("%02X", instanceSegmentType()))
        .append(") ")
        .append(this.instance);

    if (hasAttribute()) {
      description
          .append(", attribute(0x")
          .append(String.format("%02X", attributeSegmentType()))
          .append(") ")
          .append(this.attr);
    }
    return description.toString();
  }

  @Override
  public void decode(final ByteBuf buf) {
    final byte[] raw = new byte[2];
    buf.readBytes(raw);
    // available = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).getShort();

    if (raw[0] == 0x02) {
      buf.readBytes(raw);
      if (raw[0] == 0x20) {
        this.class_code = raw[1];
        this.class_name = "Ethernet Link";
      }
      buf.readBytes(raw);
      if (raw[0] == 0x24) {
        this.instance(raw[1]);
      }
    }
  }
}
