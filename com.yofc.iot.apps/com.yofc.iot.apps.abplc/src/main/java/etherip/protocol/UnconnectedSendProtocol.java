/*******************************************************************************
 * Copyright (c) 2012 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package etherip.protocol;

import static etherip.types.CNPath.ConnectionManager;
import static etherip.types.CNService.CM_Unconnected_Send;

import io.netty.buffer.ByteBuf;

/**
 * Protocol PDU that uses CM_Unconnected_Send
 *
 * @author Kay Kasemir
 */
public class UnconnectedSendProtocol extends ProtocolAdapter {

    private final ProtocolEncoder encoder;
    private final int slot;
    private final Protocol body;

    /**
     * Initialize
     *
     * @param slot
     *            Slot (0, 1, ...) of controller module in crate
     * @param body
     *            Embedded protocol for read/write
     */
    public UnconnectedSendProtocol(final int slot, final Protocol body) {
        this.encoder = new MessageRouterProtocol(CM_Unconnected_Send, ConnectionManager(), new ProtocolAdapter());
        this.slot = slot;
        this.body = body;
    }

    /** {@inheritDoc} */
    @Override
    public int getRequestSize() {
        return this.encoder.getRequestSize() + 4 + this.body.getRequestSize() + (this.needPad() ? 1 : 0) + 4;
    }

    /** {@inheritDoc} */
    @Override
    public void encode(final ByteBuf buf) {
        this.encoder.encode(buf);

        final byte tick_time = (byte) 10;
        final byte ticks = (byte) 240;
        buf.writeByte(tick_time);
        buf.writeByte(ticks);
        final short body_size = (short) this.body.getRequestSize();
        buf.writeShortLE(body_size);

        final boolean pad = this.needPad();

        this.body.encode(buf);
        if (pad) {
            buf.writeByte((byte) 0);
        }

        buf.writeByte((byte) 1); // Path size
        buf.writeByte((byte) 0); // reserved
        buf.writeByte((byte) 1); // Port 1 = backplane
        buf.writeByte((byte) this.slot);

    }

    /** {@inheritDoc} */
    @Override
    public void decode(final ByteBuf buf) {
        // CM_Unconnected_Send wraps a request.
        // The response then arrives without CM_Unconnected_Send wrapper,
        // so pass decoding on to the body.
        this.body.decode(buf);
    }

    /** @return Is path of odd length, requiring a pad byte? */
    private boolean needPad() {
        // Findbugs: x%2==1 fails for negative numbers
        return (this.body.getRequestSize() % 2) != 0;
    }
}
