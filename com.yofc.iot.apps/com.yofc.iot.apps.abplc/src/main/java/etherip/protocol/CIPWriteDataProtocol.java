/*******************************************************************************
 * Copyright (c) 2012 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package etherip.protocol;

import java.nio.ByteBuffer;

import etherip.types.CIPData;
import etherip.types.CIPData.Type;
import etherip.types.CNService;

/**
 * Protocol body for {@link CNService#CIP_WriteData}
 *
 * @author Kay Kasemir
 */
public class CIPWriteDataProtocol extends ProtocolAdapter {

    private final CIPData data;
    private final int dataOffset;
    private final short writeCount;
    private int start;
    private int requestSize = 0;

    public long getDataOffset() {
        return dataOffset;
    }

    public CIPWriteDataProtocol(final CIPData data) {
        this(data, (short) data.getElementCount(), 0, 0);
    }

    public CIPWriteDataProtocol(final CIPData data, short writeCount, int start, final int dataOffset) {
        this.data = data;
        this.dataOffset = dataOffset;
        this.writeCount = writeCount;
        this.start = start;
        calRequestSize();

    }

    private void calRequestSize() {
        int count = this.writeCount;
        if (count <= 0)
            count = this.data.getData().capacity();
        else {
            count = count * this.data.getType().getElementSize();
        }
        count += 4;
        if (dataOffset > 0)
            count += 4;
        requestSize = count;
    }

    @Override
    public int getRequestSize() {
        return this.requestSize;
    }

    @Override
    public void encode(final ByteBuffer buf, final StringBuilder log) throws Exception {
        int witeCountNum = writeCount;
        if (witeCountNum <= 0)
            witeCountNum = this.data.getElementCount();
        buf.putShort(this.data.getType().getCode());
        // STRUCT already contains structure detail, elements etc.
        // For other types, add the element count
        if (this.data.getType() == Type.STRUCT) {
            this.data.getData().clear();
            final short struct_detail = this.data.getData().getShort();
            if (struct_detail != Type.STRUCT_STRING.getCode()) {
                throw new Exception("Can only encode STRUCT_STRING, got 0x" + Integer.toHexString(struct_detail));
            }
            // The data buffer contains the string as _read_:
            // STRUCT, STRUCT_STRING, length, chars.
            // It needs to be written as
            // STRUCT, STRUCT_STRING, _elements_, length, chars.
            buf.putShort(struct_detail);

            buf.putShort((short) (this.data.getElementCount() + start));
            if (dataOffset > 0)
                buf.putInt(dataOffset);
            // Copy length, chars from data into buf
            buf.put(this.data.getData());
        } else {
            int vSize = this.data.getType().getElementSize();
            buf.putShort((short) (this.data.getElementCount() + start));
            if (dataOffset > 0)
                buf.putInt(dataOffset);

            int position = dataOffset - start * vSize;
            int length = vSize * witeCountNum;
            this.data.getData().clear();

            buf.put(this.data.getData().array(), position, length);
        }
        if (log != null) {
            log.append("USINT type, data        : ").append(this.data).append("\n");
        }
    }
}
