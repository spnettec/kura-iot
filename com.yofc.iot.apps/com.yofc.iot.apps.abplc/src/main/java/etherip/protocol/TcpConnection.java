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
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;

import etherip.util.Hexdump;

/**
 * Connection to EtherNet/IP device via TCP
 * <p>
 * Network connection as well as buffer and session info that's used for the duration of a connection.
 *
 * @author Kay Kasemir, László Pataki
 */
public class TcpConnection extends Connection {

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
        this.buffer.clear();
        encoder.encode(this.buffer);
        ByteBuffer b = this.buffer.nioBuffer();

        if (logger.isDebugEnabled()) {
            logger.debug("Data sent ({} bytes):\n{}", b.remaining(), Hexdump.toHexdump(b));
        }
        int toWrite = b.limit();
        while (toWrite > 0) {
            final int written = this.channel.write(b).get(this.timeoutMs, MILLISECONDS);
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
        ByteBuffer b = ByteBuffer.allocate(600);
        do {
            this.channel.read(b).get(this.timeoutMs, MILLISECONDS);
        } while (b.position() < getResponseSize(this.buffer));

        // Prepare to decode
        b.flip();
        this.buffer.clear();
        this.buffer.writeBytes(b);
        decoder.decode(this.buffer);

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