/*******************************************************************************
 * Copyright (c) 2012 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package etherip.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.LongAdder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import etherip.protocol.Encapsulation.Command;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * Connection to EtherNet/IP device
 * <p>
 * Network connection as well as buffer and session info that's used for the duration of a connection.
 *
 * @author Kay Kasemir, László Pataki
 */
public abstract class Connection implements AutoCloseable {

    public static final Logger logger = LoggerFactory.getLogger(Connection.class);
    private LongAdder sequenceId = new LongAdder();
    /** EtherIP uses little endian */

    private static final int BUFFER_SIZE = 600;

    protected final int slot;

    protected final ByteBuf buffer;
    protected final ByteBuffer iobuffer = ByteBuffer.allocate(600).order(ByteOrder.LITTLE_ENDIAN);

    private int session = 0;

    protected long timeoutMs = 10000;
    protected int port = 44818;

    /**
     * Initialize
     *
     * @param address
     *            IP address of device
     * @param slot
     *            Slot number 0, 1, .. of the controller within PLC crate
     * @throws Exception
     *             on error
     */
    public Connection(final String address, final int slot) throws Exception {
        logger.info("Connecting to {}:{}", address, this.port);
        this.slot = slot;

        this.buffer = Unpooled.buffer(BUFFER_SIZE);
    }

    public Connection(final String address, final int slot, int port) throws Exception {
        this.slot = slot;

        this.buffer = Unpooled.buffer(BUFFER_SIZE);
        this.port = port;
        logger.info("Connecting to {}:{}", address, this.port);
    }

    /** @return Slot number 0, 1, .. of the controller within PLC crate */
    public int getSlot() {
        return this.slot;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    /**
     * @param session
     *            Session ID to be identified with this connection
     */
    public void setSession(final int session) {
        this.session = session;
    }

    /** @return Session ID of this connection */
    public int getSession() {
        return this.session;
    }

    /** @return {@link ByteBuffer} */
    public ByteBuf getBuffer() {
        return this.buffer;
    }

    public int getResponseSize(final ByteBuffer buf) throws Exception {

        // Buffer contains header (and maybe more after that)
        final short command_code = buf.getShort(0);
        final Command cmd = Command.forCode(command_code);
        if (cmd == null) {
            throw new Exception("Received unknown command code " + command_code);
        }
        final short body_size = buf.getShort(2);

        return 24 + body_size;
    }

    public abstract boolean isOpen();

    /**
     * Write protocol data
     *
     * @param encoder
     *            {@link ProtocolEncoder} used to <code>encode</code> buffer
     * @throws Exception
     *             on error
     */
    public abstract void write(final ProtocolEncoder encoder) throws Exception;

    /**
     * Read protocol data
     *
     * @param decoder
     *            {@link ProtocolDecoder} used to <code>decode</code> buffer
     * @throws Exception
     *             on error
     */
    protected abstract void read(final ProtocolDecoder decoder) throws Exception;

    /**
     * Write protocol request and handle response
     *
     * @param protocol
     *            {@link Protocol}
     * @throws Exception
     *             on error
     */
    public synchronized void execute(final Protocol protocol) throws Exception {
        if (protocol instanceof Encapsulation) {
            sequenceId.increment();
            long senderContext = sequenceId.longValue();
            if (senderContext < 0) {
                sequenceId.reset();
                senderContext = 0;
            }
            ((Encapsulation) protocol).setSenderContext(senderContext);
        }
        this.write(protocol);
        this.read(protocol);
    }

}
