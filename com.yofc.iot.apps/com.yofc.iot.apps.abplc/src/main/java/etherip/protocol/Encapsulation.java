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
 * Encapsulation commands
 * <p>
 * Spec 4 p. 164
 *
 * @author heyoulin
 */
public class Encapsulation implements Protocol {

    /** Byte size of encapsulation header */
    public static final int ENCAPSULATION_HEADER_SIZE = 24;

    /**
     * Encapsulation commands
     * <p>
     * Spec 4 p. 164
     */
    public enum Command {
        NOP(0x0000), /* "Encapsulation Overview" slides */
        LIST_SERVICES(0x0004),
        LIST_IDENTITY(0x0063),
        LIST_INTERFACES(0x0064),
        REGISTER_SESSION(0x0065),
        UNREGISTER_SESSION(0x0066),
        SEND_RRDATA(0x006F),
        SEND_UNITDATA(0x0070),
        INDICATE_STATUS(0x0072),
        CANCEL(0x0073); /* "Encapsulation Overview" slides */

        public static Command forCode(final short code) {
            for (final Command command : values()) {
                if (command.code() == code) {
                    return command;
                }
            }
            return null;
        }

        private final short code;

        private Command(final int code) {
            this.code = (short) code;
        }

        public short code() {
            return this.code;
        }

        @Override
        public String toString() {
            return this.name() + String.format(" (0x%04X)", this.code);
        }
    }

    private long senderContext = 0;

    public long getSenderContext() {
        return senderContext;
    }

    public void setSenderContext(long senderContext) {
        this.senderContext = senderContext;
    }

    private final Command command;
    private int session;
    private final Protocol body;

    public Encapsulation(final Command command, final int session, final Protocol body) {
        this.command = command;
        this.session = session;
        this.body = body;
    }

    /** {@inheritDoc} */
    @Override
    public int getRequestSize() {
        return ENCAPSULATION_HEADER_SIZE + this.body.getRequestSize();
    }

    /** {@inheritDoc} */
    @Override
    public void encode(final ByteBuffer buf) {
        final int status = 0;
        final int options = 0;

        buf.putShort(this.command.code());
        buf.putShort((short) this.body.getRequestSize());
        buf.putInt(this.session);
        buf.putInt(status);
        buf.putLong(this.senderContext);
        buf.putInt(options);

        this.body.encode(buf);
    }

    /** {@inheritDoc} */
    @Override
    public int getResponseSize() {
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public void decode(final ByteBuffer buf) {
        // Start decoding
        final short command_code = buf.getShort();
        final Command cmd = Command.forCode(command_code);
        if (cmd == null) {
            throw new CodedcException("Received unknown command code " + command_code);
        }
        if (cmd != this.command) {
            throw new CodedcException("Received command " + command + " instead of " + this.command);
        }

        final short body_size = buf.getShort();

        final int ses = buf.getInt();
        if (ses != this.session) {
            // If we did not have a session, remember the newly obtained session
            if (this.session == 0) {
                this.session = ses;
            } else {
                throw new CodedcException("Received session " + session + " instead of " + this.session);
            }
        }

        final int status = buf.getInt();

        final long recvdContext = buf.getLong();
        if (recvdContext != this.senderContext)
            throw new CodedcException("Received context " + recvdContext + ", expected " + this.senderContext);
        // int options
        buf.getInt();

        if (status != 0) {
            throw new CodedcException(
                    String.format("Received status 0x%08X (%s)%n", status, this.getStatusMessage(status)));
        }
        if (buf.remaining() < body_size) {
            throw new CodedcException("Need " + body_size + " more bytes, have " + buf.remaining());
        }

        this.body.decode(buf);
    }

    private String getStatusMessage(final int status) {
        switch (status) {
        case 0x00:
            return "OK";
        case 0x01:
            return "invalid/unsupported command";
        case 0x02:
            return "no memory on target";
        case 0x03:
            return "malformed data in request";
        case 0x64:
            return "invalid session ID";
        case 0x65:
            return "invalid data length";
        case 0x69:
            return "unsupported protocol revision";
        default:
        }
        return "<unknown>";
    }

    /** @return Session ID */
    public final int getSession() {
        return this.session;
    }
}
