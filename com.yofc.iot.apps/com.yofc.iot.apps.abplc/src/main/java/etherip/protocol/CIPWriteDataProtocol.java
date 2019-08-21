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
    }

    @Override
    public void encode(final ByteBuf buf) {
        int witeCountNum = writeCount;
        if (witeCountNum <= 0)
            witeCountNum = this.data.getElementCount();
        buf.writeShortLE(this.data.getType().getCode());
        // STRUCT already contains structure detail, elements etc.
        // For other types, add the element count
        this.data.getData().clear();
        if (this.data.getType() == Type.STRUCT) {

            final short struct_detail = this.data.getData().getShort();
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
            int length = this.data.getData().remaining();
            int offset = data.getData().limit() + 84 - (length > 84 ? 84 : length);
            data.getData().limit(offset);
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
