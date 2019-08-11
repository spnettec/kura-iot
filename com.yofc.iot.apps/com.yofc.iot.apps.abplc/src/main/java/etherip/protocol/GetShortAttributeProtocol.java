/*******************************************************************************
 * Copyright (c) 2012 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package etherip.protocol;

import io.netty.buffer.ByteBuf;

/**
 * Decode a <code>short</code> attribute
 *
 * @author Kay Kasemir
 */
public class GetShortAttributeProtocol extends ProtocolAdapter {

    private short value;

    /** {@inheritDoc} */
    @Override
    public void decode(final ByteBuf buf) {
        this.value = buf.readShortLE();
    }

    /** @return Value read from response */
    public final short getValue() {
        return this.value;
    }
}
