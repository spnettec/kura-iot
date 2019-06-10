/*******************************************************************************
 * Copyright (c) 2017 NETvisor Ltd.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package etherip.protocol;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousSocketChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import etherip.util.Hexdump;

/**
 * Connection to EtherNet/IP device via TCP
 * <p>
 * Network connection as well as buffer and session info that's used for the duration of a connection.
 *
 * @author Kay Kasemir, László Pataki
 */
public class TcpConnection extends Connection {

    public static final Logger logger = LoggerFactory.getLogger(TcpConnection.class);

    private final AsynchronousSocketChannel channel;

    /**
     * Initialize
     *
     * @param address
     *            IP address of device
     * @param slot
     *            Slot number 0, 1, .. of the controller within PLC crate
     * @throws Exception
     */
    public TcpConnection(final String address, final int slot) throws Exception {
        super(address, slot);

        this.channel = AsynchronousSocketChannel.open();
        this.channel.connect(new InetSocketAddress(address, this.port)).get(this.timeoutMs, MILLISECONDS);
    }

    public TcpConnection(final String address, final int slot, final int port) throws Exception {
        super(address, slot, port);

        this.channel = AsynchronousSocketChannel.open();
        this.channel.connect(new InetSocketAddress(address, this.port)).get(this.timeoutMs, MILLISECONDS);
    }

    /**
     * Write protocol data
     *
     * @param encoder
     *            {@link ProtocolEncoder} used to <code>encode</code> buffer
     * @throws Exception
     *             on error
     */
    @Override
    public void write(final ProtocolEncoder encoder) throws Exception {
        final StringBuilder log = logger.isDebugEnabled() ? new StringBuilder() : null;
        this.buffer.clear();
        encoder.encode(this.buffer, log);
        if (log != null) {
            logger.debug("Protocol Encoding\n{}", log);
        }
        this.buffer.flip();
        if (logger.isDebugEnabled()) {
            logger.debug("Data sent ({} bytes):\n{}", this.buffer.remaining(), Hexdump.toHexdump(this.buffer));
        }
        int toWrite = this.buffer.limit();
        while (toWrite > 0) {
            final int written = this.channel.write(this.buffer).get(this.timeoutMs, MILLISECONDS);
            toWrite -= written;
            if (toWrite > 0) {
                this.buffer.compact();
            }
        }
    }

    /**
     * Read protocol data
     *
     * @param decoder
     *            {@link ProtocolDecoder} used to <code>decode</code> buffer
     * @throws Exception
     *             on error
     */
    @Override
    public void read(final ProtocolDecoder decoder) throws Exception {
        // Read until protocol has enough data to decode
        this.buffer.clear();
        do {
            this.channel.read(this.buffer).get(this.timeoutMs, MILLISECONDS);
        } while (this.buffer.position() < decoder.getResponseSize(this.buffer));

        // Prepare to decode
        this.buffer.flip();

        if (logger.isDebugEnabled()) {
            logger.debug("Data read ({} bytes):\n{}", this.buffer.remaining(), Hexdump.toHexdump(this.buffer));
        }

        final StringBuilder log = logger.isDebugEnabled() ? new StringBuilder() : null;
        try {
            decoder.decode(this.buffer, this.buffer.remaining(), log);
        } finally { // Show log even on error
            if (log != null) {
                logger.debug("Protocol Decoding\n{}", log);
            }
        }
    }

    @Override
    public void close() throws IOException {
        this.channel.close();
    }

    @Override
    public boolean isOpen() {
        return this.channel.isOpen();
    }

}