/*******************************************************************************
 * Copyright (c) 2012 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package etherip.protocol;

import etherip.util.CodedcException;
import io.netty.buffer.ByteBuf;

/**
 * SendRRData, the unconnected Request/Response protocol
 *
 * @author Kay Kasemir
 */
public class SendRRDataProtocol extends ProtocolAdapter {

  /** Byte size of encapsulation header */
  public static final int RR_DATA_HEADER_SIZE = 16;

  private final Protocol body;

  /**
   * Initialize
   *
   * @param body Protocol to place in body of RRData
   */
  public SendRRDataProtocol(final Protocol body) {
    this.body = body;
  }

  /** {@inheritDoc} */
  @Override
  public void encode(final ByteBuf buf) {
    final short addr_type = 0;
    final short data_type = 0xB2;
    buf.writeIntLE(0);
    buf.writeShortLE((short) 0);
    buf.writeShortLE((short) 2);
    buf.writeShortLE(addr_type);
    buf.writeShortLE((short) 0);
    buf.writeShortLE(data_type);
    // buf.writeShortLE((short) this.body.getRequestSize());

    encodeEmbeddedService(buf);
  }

  private int encodeEmbeddedService(ByteBuf buf) {
    // length of embedded message
    int lengthStartIndex = buf.writerIndex();
    buf.writeShortLE(0);

    // embedded message
    int messageStartIndex = buf.writerIndex();
    this.body.encode(buf);

    // go back and update length
    int bytesWritten = buf.writerIndex() - messageStartIndex;
    buf.markWriterIndex();
    buf.writerIndex(lengthStartIndex);
    buf.writeShortLE(bytesWritten);
    buf.resetWriterIndex();

    return bytesWritten;
  }

  /** {@inheritDoc} */
  @Override
  public void decode(final ByteBuf buf) {
    final int iface = buf.readIntLE();
    if (iface != 0) {
      throw new CodedcException("Received interface " + iface + " instead of 0");
    }
    // short timeout
    buf.readShortLE();
    final short count = buf.readShortLE();
    if (count != 2) {
      throw new CodedcException("Received count " + count + " instead of 2");
    }
    // short addr_type
    buf.readShortLE();
    // short addr_length
    buf.readShortLE();
    // short data_type
    buf.readShortLE();
    // short received_data_length
    buf.readShortLE();

    // Followed by data...
    this.body.decode(buf);
  }

  /**
   * Decode IDs for "Common Packet Type" (address and data IDs)
   *
   * <p>Spec, 8.9.1
   */
  /*
   * private static String decodeCPT(final short id) {
   * switch (id) {
   * case (short) 0x0000:
   * return "UCMM";
   * case (short) 0x00A1:
   * return "connection based";
   * case (short) 0x8000:
   * return "sockaddr, orig->tgt.";
   * case (short) 0x8001:
   * return "sockaddr, tgt.->orig";
   * case (short) 0x8002:
   * return "sequenced address";
   * case (short) 0x00B1:
   * return "Connected PDU";
   * case (short) 0x00B2:
   * return "Unconnected Message";
   * default:
   * }
   * return "<unknown>";
   * }
   */
}
