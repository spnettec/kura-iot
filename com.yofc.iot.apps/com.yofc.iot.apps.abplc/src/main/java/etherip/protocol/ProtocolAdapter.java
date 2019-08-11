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
 * Base for protocol handler with NOP implementations
 *
 * @author Kay Kasemir
 */
public class ProtocolAdapter implements Protocol {

    /** {@inheritDoc} */
    @Override
    public int getRequestSize() {
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public void encode(final ByteBuf buf) {
        // NOP
    }

    /** {@inheritDoc} */
    @Override
    public int getResponseSize() {
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public void decode(final ByteBuf buf) {
        // NOP
    }
}
