/*******************************************************************************
 * Copyright (c) 2012 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package etherip.protocol;

import java.nio.ByteBuffer;

import etherip.data.CipException;
import etherip.types.CNPath;
import etherip.types.CNService;
import etherip.util.CodedcException;

/**
 * Message Router PDU (Protocol Data Unit)
 *
 * @author Kay Kasemir, László Pataki
 */
public class MessageRouterProtocol extends ProtocolAdapter {

    private final CNService service;

    private final CNPath path;

    protected final Protocol body;

    private int status = 0;

    private int[] extStatus = new int[0];

    private boolean partialTransfert = false;

    private int payLoad;

    public int getPayLoad() {
        return payLoad;
    }

    /**
     * Initialize
     *
     * @param service
     *            Service for request
     * @param path
     *            Path for request
     * @param body
     *            Protocol embedded in the message request/response
     */
    public MessageRouterProtocol(final CNService service, final CNPath path, final Protocol body) {
        this.service = service;
        this.path = path;
        this.body = body;
    }

    /** {@inheritDoc} */
    @Override
    public int getRequestSize() {
        return 2 + this.path.getRequestSize() + this.body.getRequestSize();
    }

    /**
     * {@inheritDoc}
     *
     * @throws Exception
     */
    @Override
    public void encode(final ByteBuffer buf) {
        buf.put(this.service.getCode());
        this.path.encode(buf);

        this.body.encode(buf);
    }

    /** {@inheritDoc} */
    @Override
    public int getResponseSize() {
        throw new IllegalStateException("Unknown response size");
    }

    /** {@inheritDoc} */
    @Override
    public void decode(final ByteBuffer buf) {
        int available = buf.remaining();
        final byte service_code = buf.get();
        final CNService reply = CNService.forCode(service_code);
        if (reply == null) {
            throw new CodedcException(
                    "Received reply with unknown service code 0x" + Integer.toHexString(service_code));
        }
        if (!reply.isReply()) {
            throw new CodedcException("Expected reply, got " + reply);
        }
        // int reserved
        buf.get();
        this.status = buf.get();
        final int ext_status_size = buf.get();
        this.extStatus = new int[ext_status_size];
        for (int i = 0; i < ext_status_size; ++i) {
            this.extStatus[i] = buf.getShort();
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
