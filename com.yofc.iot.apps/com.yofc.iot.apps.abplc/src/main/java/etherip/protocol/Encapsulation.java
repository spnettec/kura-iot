/*******************************************************************************
 * Copyright (c) 2012 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package etherip.protocol;

import etherip.util.CodedcException;
import io.netty.buffer.ByteBuf;

/**
 * Encapsulation commands
 *
 * <p>Spec 4 p. 164
 *
 * @author heyoulin
 */
public class Encapsulation implements Protocol {

  /** Byte size of encapsulation header */
  public static final int ENCAPSULATION_HEADER_SIZE = 24;
  private final Command command;
  private final Protocol body;
  private long senderContext = 0;
  private int session;

  public Encapsulation(final Command command, final int session, final Protocol body) {
    this.command = command;
    this.session = session;
    this.body = body;
  }

  public long getSenderContext() {
    return senderContext;
  }

  public void setSenderContext(long senderContext) {
    this.senderContext = senderContext;
  }

  /** {@inheritDoc} */
  @Override
  public void encode(final ByteBuf buf) {
    buf.writeShortLE(this.command.code());
    encodeEmbeddedService(buf);
  }

  private int encodeEmbeddedService(ByteBuf buf) {
    final int status = 0;
    final int options = 0;
    // length of embedded message
    int lengthStartIndex = buf.writerIndex();
    buf.writeShortLE(0);
    buf.writeIntLE(this.session);
    buf.writeIntLE(status);
    buf.writeLongLE(this.senderContext);
    buf.writeIntLE(options);

    // embedded message
    int messageStartIndex = buf.writerIndex();
    this.body.encode(buf);

    // go back and update length
    int bytesWritten = buf.writerIndex() - messageStartIndex;
    buf.markWriterIndex();
    buf.writerIndex(lengthStartIndex);
    buf.writeShortLE(bytesWritten);
    buf.resetWriterIndex();

    return bytesWritten;
  }

  /** {@inheritDoc} */
  @Override
  public void decode(final ByteBuf buf) {
    // Start decoding
    final short command_code = buf.readShortLE();
    final Command cmd = Command.forCode(command_code);
    if (cmd == null) {
      throw new CodedcException("Received unknown command code " + command_code);
    }
    if (cmd != this.command) {
      throw new CodedcException("Received command " + command + " instead of " + this.command);
    }

    final short body_size = buf.readShortLE();

    final int ses = buf.readIntLE();
    if (ses != this.session) {
      // If we did not have a session, remember the newly obtained session
      if (this.session == 0) {
        this.session = ses;
      } else {
        throw new CodedcException("Received session " + session + " instead of " + this.session);
      }
    }

    final int status = buf.readIntLE();

    final long recvdContext = buf.readLongLE();
    if (recvdContext != this.senderContext) {
      throw new CodedcException(
          "Received context " + recvdContext + ", expected " + this.senderContext);
    }
    // int options
    buf.readIntLE();

    if (status != 0) {
      throw new CodedcException(
          String.format("Received status 0x%08X (%s)%n", status, this.getStatusMessage(status)));
    }
    if (buf.readableBytes() < body_size) {
      throw new CodedcException("Need " + body_size + " more bytes, have " + buf.readableBytes());
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

  /**
   * Encapsulation commands
   *
   * <p>Spec 4 p. 164
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

    private final short code;

    Command(final int code) {
      this.code = (short) code;
    }

    public static Command forCode(final short code) {
      for (final Command command : values()) {
        if (command.code() == code) {
          return command;
        }
      }
      return null;
    }

    public short code() {
      return this.code;
    }

    @Override
    public String toString() {
      return this.name() + String.format(" (0x%04X)", this.code);
    }
  }
}
