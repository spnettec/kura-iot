/*******************************************************************************
 * Copyright (c) 2012 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package etherip.protocol;

import etherip.types.CNService;
import etherip.util.CodedcException;
import io.netty.buffer.ByteBuf;

/**
 * Protocol for {@link CNService#CIP_MultiRequest}
 *
 * <p>Handles several embedded {@link MessageRouterProtocol} read or write requests.
 *
 * @author Kay Kasemir
 */
public class CIPMultiRequestProtocol extends ProtocolAdapter {

  private final MessageRouterProtocol[] services;

  public CIPMultiRequestProtocol(final MessageRouterProtocol... services) {
    this.services = services;
  }

  @Override
  public void encode(final ByteBuf buf) {
    int serviceCount = this.services.length;

    buf.writeShortLE(serviceCount);

    int[] offsets = new int[serviceCount];
    int offsetsStartIndex = buf.writerIndex();
    buf.writeZero(serviceCount * 2);

    for (int i = 0; i < serviceCount; i++) {
      offsets[i] = buf.writerIndex() - offsetsStartIndex + 2;
      this.services[i].encode(buf);
    }

    buf.markWriterIndex();
    buf.writerIndex(offsetsStartIndex);
    for (int offset : offsets) {
      buf.writeShortLE(offset);
    }
    buf.resetWriterIndex();
  }

  @Override
  public void decode(final ByteBuf buf) {
    int available = buf.readableBytes();
    final int start = buf.readerIndex();

    // Count
    final short count = buf.readShortLE();

    // Offset table
    final short[] offset = new short[count];
    for (int i = 0; i < count; ++i) {
      offset[i] = buf.readShortLE();
    }

    // Individual replies
    buf.markWriterIndex();
    for (int i = 0; i < count; ++i) { // Track buffer offset from start
      final int off = buf.readerIndex() - start;
      if (off != offset[i]) {
        throw new CodedcException(
            "Expected response #" + (i + 1) + " at offset " + offset[i] + ", not " + off);
      }

      // Determine length of this section
      final int section_length;
      if (i < count - 1) {
        section_length = offset[i + 1] - off; // .. from offset table
      } else {
        section_length = available - offset[i]; // .. from distance to end for last section
      }
      buf.writerIndex(buf.readerIndex() + section_length);
      this.services[i].decode(buf);
    }
    buf.resetWriterIndex();
  }
}
