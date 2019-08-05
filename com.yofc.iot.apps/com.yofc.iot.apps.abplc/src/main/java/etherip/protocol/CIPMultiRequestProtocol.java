/*******************************************************************************
 * Copyright (c) 2012 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package etherip.protocol;

import java.nio.ByteBuffer;

import etherip.types.CNService;

/**
 * Protocol for {@link CNService#CIP_MultiRequest}
 * <p>
 * Handles several embedded {@link MessageRouterProtocol} read or write requests.
 *
 * @author Kay Kasemir
 */
public class CIPMultiRequestProtocol extends ProtocolAdapter {

    private final MessageRouterProtocol[] services;

    public CIPMultiRequestProtocol(final MessageRouterProtocol... services) {
        this.services = services;
    }

    @Override
    public int getRequestSize() {
        // Size: 'count' + offset to each service
        int total = 2 + 2 * this.services.length;
        // ..plus bytes of each service itself
        for (final ProtocolAdapter service : this.services) {
            total += service.getRequestSize();
        }
        return total;
    }

    @Override
    public void encode(final ByteBuffer buf) throws Exception {
        // final int start = buf.position();

        final short count = (short) this.services.length;
        // Encode service count
        buf.putShort(count);

        // Encode offsets to individual requests
        // Offset to 1st item:
        // 2 bytes for 'count', 2 bytes for each offset
        short offset = (short) (2 + 2 * count);
        for (int i = 0; i < count; ++i) {
            buf.putShort(offset);

            // Next offset: After bytes for this request
            offset += this.services[i].getRequestSize();
        }

        for (int i = 0; i < count; ++i) {

            this.services[i].encode(buf);

        }
    }

    @Override
    public int getResponseSize() throws Exception {
        int total = 0;
        for (final ProtocolAdapter service : this.services) {
            total += service.getResponseSize();
        }
        return total;
    }

    @Override
    public void decode(final ByteBuffer buf) throws Exception {
        int available = buf.remaining();
        final int start = buf.position();

        // Count
        final short count = buf.getShort();

        // Offset table
        final short[] offset = new short[count];
        for (int i = 0; i < count; ++i) {
            offset[i] = buf.getShort();

        }

        // Individual replies
        int originLimit = buf.limit();
        for (int i = 0; i < count; ++i) { // Track buffer offset from start
            final int off = buf.position() - start;
            if (off != offset[i]) {
                throw new Exception("Expected response #" + (i + 1) + " at offset " + offset[i] + ", not " + off);
            }

            // Determine length of this section
            final int section_length;
            if (i < count - 1) {
                section_length = offset[i + 1] - off; // .. from offset table
            } else {
                section_length = available - offset[i]; // .. from distance to end for last section
            }
            buf.limit(buf.position() + section_length);
            this.services[i].decode(buf);
        }
        buf.limit(originLimit);
    }
}
