/*******************************************************************************
 * Copyright (c) 2012 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package etherip.protocol;

import etherip.data.CipException;
import etherip.types.CNPath;
import etherip.types.CNService;
import etherip.util.CodedcException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;

/**
 * Message Router PDU (Protocol Data Unit)
 *
 * @author Kay Kasemir, László Pataki
 */
public class MessageRouterProtocol extends ProtocolAdapter {

  protected final Protocol body;
  private final CNService service;
  private final CNPath path;
  private int status = 0;

  private int[] extStatus = new int[0];

  private boolean partialTransfert = false;

  private int payLoad;

  /**
   * Initialize
   *
   * @param service Service for request
   * @param path Path for request
   * @param body Protocol embedded in the message request/response
   */
  public MessageRouterProtocol(final CNService service, final CNPath path, final Protocol body) {
    this.service = service;
    this.path = path;
    this.body = body;
  }

  public int getPayLoad() {
    return payLoad;
  }

  /** {@inheritDoc} */
  // @Override
  public int getRequestSize() {
    ByteBuf buf = Unpooled.buffer(600);
    try {
      this.encode(buf);
      return buf.writerIndex();
    } finally {
      ReferenceCountUtil.release(buf);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @throws Exception
   */
  @Override
  public void encode(final ByteBuf buf) {
    buf.writeByte(this.service.getCode());
    this.path.encode(buf);

    this.body.encode(buf);
  }

  /** {@inheritDoc} */
  @Override
  public void decode(final ByteBuf buf) {
    int available = buf.readableBytes();
    final byte service_code = buf.readByte();
    final CNService reply = CNService.forCode(service_code);
    if (reply == null) {
      throw new CodedcException(
          "Received reply with unknown service code 0x" + Integer.toHexString(service_code));
    }
    if (!reply.isReply()) {
      throw new CodedcException("Expected reply, got " + reply);
    }
    // int reserved
    buf.readByte();
    this.status = buf.readByte();
    final int ext_status_size = buf.readByte();
    this.extStatus = new int[ext_status_size];
    for (int i = 0; i < ext_status_size; ++i) {
      this.extStatus[i] = buf.readShortLE();
    }

    // Followed by data...

    final CNService expected_reply = this.service.getReply();
    if (this.status != 0) {
      if (this.status == 6) { // Not an error, we need to ask for remaining
        this.partialTransfert = true;
      } else {
        if (ext_status_size > 0) {
          throw new CipException(this.status, this.extStatus[0]);
        } else {
          throw new CipException(this.status, 0);
        }
      }
    }

    if (expected_reply != null && expected_reply != reply) {
      throw new CodedcException("Expected " + expected_reply + ", got " + reply);
    }
    int bodySize = available - 4 - 2 * ext_status_size;
    this.payLoad = bodySize - 2;
    this.body.decode(buf);
  }

  public boolean isPartialTransfert() {
    return partialTransfert;
  }

  /** @return Status code of response */
  public int getStatus() {
    return this.status;
  }

  /** @return Extended status codes of response */
  public int[] getExtendedStatus() {
    return this.extStatus;
  }
}
