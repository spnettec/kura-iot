/*******************************************************************************
 * Copyright (c) 2012 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package etherip.protocol;

import etherip.types.CIPData;
import etherip.types.CNService;
import io.netty.buffer.ByteBuf;

/**
 * Protocol body for {@link CNService#CIP_ReadData}
 *
 * @author Kay Kasemir
 */
public class CIPReadDataProtocol extends ProtocolAdapter {

    private CIPData data;
    private final short count;
    private final int startBytes;

    /**
     * Create a read protocol message that requests a single element
     */
    public CIPReadDataProtocol() {
        this.count = 1;
        this.startBytes = 0;
    }

    /**
     * Create a read protocol message that requests one or more elements if request is an array
     *
     * @param count
     */

    public CIPReadDataProtocol(final int start, final short count) {
        this.count = count;
        this.startBytes = start;
    }

    @Override
    public void encode(final ByteBuf buf) {
        buf.writeShortLE(this.count); // elements
        if (this.startBytes > 0)
            buf.writeIntLE(this.startBytes);
    }

    @Override
    public void decode(final ByteBuf buf) {
        int available = buf.readableBytes();
        if (available <= 0) {
            this.data = null;

            return;
        }
        final CIPData.Type type = CIPData.Type.forCode(buf.readShortLE());
        final byte[] raw = new byte[available - 2];
        buf.readBytes(raw);
        this.data = new CIPData(type, raw);

    }

    public final CIPData getData() {
        return this.data;
    }
}
