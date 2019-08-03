/*******************************************************************************
 * Copyright (c) 2012 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package etherip.protocol;

import java.nio.ByteBuffer;

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
     * @param body
     *            Protocol to place in body of RRData
     */
    public SendRRDataProtocol(final Protocol body) {
        this.body = body;
    }

    /** {@inheritDoc} */
    @Override
    public int getRequestSize() {
        return RR_DATA_HEADER_SIZE + this.body.getRequestSize();
    }

    /** {@inheritDoc} */
    @Override
    public void encode(final ByteBuffer buf) throws Exception {
        final short addr_type = 0;
        final short data_type = 0xB2;
        buf.putInt(0);
        buf.putShort((short) 0);
        buf.putShort((short) 2);
        buf.putShort(addr_type);
        buf.putShort((short) 0);
        buf.putShort(data_type);
        buf.putShort((short) this.body.getRequestSize());

        this.body.encode(buf);
    }

    /** {@inheritDoc} */
    @Override
    public void decode(final ByteBuffer buf) throws Exception {
        final int iface = buf.getInt();
        if (iface != 0) {
            throw new Exception("Received interface " + iface + " instead of 0");
        }
        // short timeout
        buf.getShort();
        final short count = buf.getShort();
        if (count != 2) {
            throw new Exception("Received count " + count + " instead of 2");
        }
        // short addr_type
        buf.getShort();
        // short addr_length
        buf.getShort();
        // short data_type
        buf.getShort();
        // short received_data_length
        buf.getShort();

        // Followed by data...
        this.body.decode(buf);
    }

    /**
     * Decode IDs for "Common Packet Type" (address and data IDs)
     * <p>
     * Spec, 8.9.1
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
