/*******************************************************************************
 * Copyright (c) 2017 NETvisor Ltd.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package etherip.protocol;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import etherip.util.Hexdump;
import io.netty.buffer.ByteBuf;

/**
 * Connection to EtherNet/IP device via UDP
 * <p>
 * Network connection as well as buffer and session info that's used for the duration of a connection.
 *
 * @author Kay Kasemir, László Pataki
 */
public class UdpConnection extends Connection {

    public static final Logger logger = LoggerFactory.getLogger(UdpConnection.class);

    // private final DatagramChannel receiveChannel;
    private final DatagramChannel channel;

    /**
     * Initialize
     *
     * @param address
     *            IP address of device
     * @param slot
     *            Slot number 0, 1, .. of the controller within PLC crate
     * @throws Exception
     */
    public UdpConnection(final String address, final int slot) throws Exception {
        super(address, slot);

        this.channel = DatagramChannel.open();

        this.channel.connect(new InetSocketAddress(address, this.port));
    }

    public UdpConnection(final String address, final int slot, final int port) throws Exception {
        super(address, slot, port);

        this.channel = DatagramChannel.open();

        this.channel.connect(new InetSocketAddress(address, this.port));
    }

    /** @return Slot number 0, 1, .. of the controller within PLC crate */
    @Override
    public int getSlot() {
        return this.slot;
    }

    /** @return {@link ByteBuffer} */
    @Override
    public ByteBuf getBuffer() {
        return this.buffer;
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
        this.buffer.clear();
        encoder.encode(this.buffer);
        ByteBuffer b = this.buffer.nioBuffer();

        b.flip();
        if (logger.isInfoEnabled()) {
            logger.info("Data sent ({} bytes):\n{}", b.remaining(), Hexdump.toHexdump(b));
        }
        int toWrite = b.limit();
        while (toWrite > 0) {
            final int written = this.channel.write(b);
            toWrite -= written;
            if (toWrite > 0) {
                b.compact();
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
        ByteBuffer b = this.buffer.nioBuffer();
        if (this.channel.isBlocking()) {
            this.channel.socket().setSoTimeout((int) this.timeoutMs);
        }
        do {
            this.channel.receive(b);
        } while (b.position() < getResponseSize(this.buffer));
        // Prepare to decode
        b.flip();
        if (logger.isInfoEnabled()) {
            logger.info("Data read ({} bytes):\n{}", b.remaining(), Hexdump.toHexdump(b));
        }

        decoder.decode(this.buffer);

    }

    @Override
    public void close() throws Exception {
        this.channel.close();
    }

    @Override
    public boolean isOpen() {
        return this.channel.isOpen();
    }

}