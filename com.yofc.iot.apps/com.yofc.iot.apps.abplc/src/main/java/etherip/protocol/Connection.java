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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Connection to EtherNet/IP device
 * <p>
 * Network connection as well as buffer and session info that's used for the duration of a connection.
 *
 * @author Kay Kasemir, László Pataki
 */
public abstract class Connection implements AutoCloseable {

    public static final Logger logger = LoggerFactory.getLogger(Connection.class);

    /** EtherIP uses little endian */
    public static final ByteOrder BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;

    private static final int BUFFER_SIZE = 600;

    protected final int slot;

    protected final ByteBuffer buffer;

    private int session = 0;

    protected long timeoutMs = 10000;
    protected int port = 44818;
    private Lock lock = new ReentrantLock();

    /**
     * Initialize
     *
     * @param address
     *                    IP address of device
     * @param slot
     *                    Slot number 0, 1, .. of the controller within PLC crate
     * @throws Exception
     *                       on error
     */
    public Connection(final String address, final int slot) throws Exception {
        logger.info("Connecting to {}:{}", address, this.port);
        this.slot = slot;

        this.buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
        this.buffer.order(BYTE_ORDER);
    }

    public Connection(final String address, final int slot, int port) throws Exception {
        this.slot = slot;

        this.buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
        this.buffer.order(BYTE_ORDER);
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
     *                    Session ID to be identified with this connection
     */
    public void setSession(final int session) {
        this.session = session;
    }

    /** @return Session ID of this connection */
    public int getSession() {
        return this.session;
    }

    /** @return {@link ByteBuffer} */
    public ByteBuffer getBuffer() {
        return this.buffer;
    }

    public abstract boolean isOpen();

    /**
     * Write protocol data
     *
     * @param encoder
     *                    {@link ProtocolEncoder} used to <code>encode</code> buffer
     * @throws Exception
     *                       on error
     */
    public abstract void write(final ProtocolEncoder encoder) throws Exception;

    /**
     * Read protocol data
     *
     * @param decoder
     *                    {@link ProtocolDecoder} used to <code>decode</code> buffer
     * @throws Exception
     *                       on error
     */
    protected abstract void read(final ProtocolDecoder decoder) throws Exception;

    /**
     * Write protocol request and handle response
     *
     * @param protocol
     *                     {@link Protocol}
     * @throws Exception
     *                       on error
     */
    public synchronized void execute(final Protocol protocol) throws Exception {
        try {
            lock.tryLock(this.timeoutMs, TimeUnit.MILLISECONDS);
            this.write(protocol);
            this.read(protocol);
        } finally {
            lock.unlock();
        }
    }

}
