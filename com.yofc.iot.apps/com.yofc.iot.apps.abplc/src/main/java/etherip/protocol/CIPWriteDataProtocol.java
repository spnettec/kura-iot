/*******************************************************************************
 * Copyright (c) 2012 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package etherip.protocol;

import etherip.types.CIPData;
import etherip.types.CIPData.Type;
import etherip.types.CNService;
import etherip.util.CodedcException;
import io.netty.buffer.ByteBuf;

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
        this(data, (short) 0, 0, 0);
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
    public void encode(final ByteBuf buf) {
        int witeCountNum = writeCount;
        if (witeCountNum <= 0)
            witeCountNum = this.data.getElementCount();
        buf.writeShortLE(this.data.getType().getCode());
        // STRUCT already contains structure detail, elements etc.
        // For other types, add the element count
        if (this.data.getType() == Type.STRUCT) {
            final short struct_detail = this.data.getData().readShortLE();
            if (struct_detail != Type.STRUCT_STRING.getCode()) {
                throw new CodedcException("Can only encode STRUCT_STRING, got 0x" + Integer.toHexString(struct_detail));
            }
            // The data buffer contains the string as _read_:
            // STRUCT, STRUCT_STRING, length, chars.
            // It needs to be written as
            // STRUCT, STRUCT_STRING, _elements_, length, chars.
            buf.writeShortLE(struct_detail);

            buf.writeShortLE((short) (this.data.getElementCount() + start));
            if (dataOffset > 0)
                buf.writeIntLE(dataOffset);
            // Copy length, chars from data into buf
            int length = this.data.getData().readableBytes();
            int offset = data.getData().writerIndex() + 88 - (length > 88 ? 88 : length);
            data.getData().writerIndex(offset);
            buf.writeBytes(this.data.getData());
        } else {
            int vSize = this.data.getType().getElementSize();
            buf.writeShortLE((short) (this.data.getElementCount() + start));
            if (dataOffset > 0)
                buf.writeIntLE(dataOffset);

            int position = dataOffset - start * vSize;
            int length = vSize * witeCountNum;

            buf.writeBytes(this.data.getData().array(), position, length);
        }

    }
}
