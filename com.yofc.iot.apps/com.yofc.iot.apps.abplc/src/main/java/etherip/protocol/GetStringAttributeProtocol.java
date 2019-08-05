/*******************************************************************************
 * Copyright (c) 2012 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package etherip.protocol;

import java.nio.ByteBuffer;

import etherip.util.CodedcException;

/**
 * Decode a <code>String</code> attribute
 *
 * @author Kay Kasemir
 */
public class GetStringAttributeProtocol extends ProtocolAdapter {

    private String value;

    /** {@inheritDoc} */
    @Override
    public void decode(final ByteBuffer buf) {
        final int len = buf.get() & 0x7F;
        if (len > buf.remaining()) {
            throw new CodedcException(
                    "String length of " + len + " exceeds remaining data (" + (buf.remaining()) + " bytes)");
        }
        final byte[] raw = new byte[len];
        buf.get(raw);
        this.value = new String(raw);

        // Skip remaining bytes
        /*
         * final int rest = available - 1 - len;
         * if (rest > 0) {
         * buf.position(buf.position() + rest);
         * }
         */
    }

    /** @return Value read from response */
    public final String getValue() {
        return this.value;
    }
}
