/*******************************************************************************
 * Copyright (c) 2017 NETvisor Ltd.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package etherip.protocol;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import etherip.data.ConnectionData;
import io.netty.buffer.ByteBuf;

/**
 * Connection Data decoder
 *
 * @see CIP_Vol2_1.4: 3-5.5.5
 * @author László Pataki
 */
public class GetConnectionDataProtocol extends ProtocolAdapter {

    private ConnectionData connectionData;

    public static final Logger log = LoggerFactory.getLogger(GetConnectionDataProtocol.class);

    @Override
    public void decode(final ByteBuf buf) {
        this.connectionData = new ConnectionData();

        // Service
        final byte[] raw = new byte[1];
        buf.readBytes(raw);
        // CIP Connection Manager byte
        buf.readBytes(raw);
        // Statuses
        buf.readBytes(raw);
        this.connectionData.setConnectionGeneralStatus(raw[0]);
        buf.readBytes(raw);
        this.connectionData.setConnectionAdditionalStatus(raw[0]);

        log.debug("ConnectionData value      : {}", this.connectionData);

        // Skip remaining bytes
        /*
         * final int rest = available - 1 - 1 - 1 - 1;
         * if (rest > 0) {
         * if (log.isWarnEnabled()) {
         * final byte[] arr = new byte[buf.remaining()];
         * buf.get(arr);
         * log.warn("\nRemaining bytes: {}", bytesToHex(arr));
         * }
         * buf.position(buf.position() + rest);
         * }
         */
        // if (this.connectionData.getConnectionGeneralStatus() != 0) {
        // throw new CipException(this.connectionData.getConnectionGeneralStatus(), (byte) 0);
        // }
    }

    /** @return Value read from response */
    public final ConnectionData getValue() {
        return this.connectionData;
    }

    public static String bytesToHex(final byte[] bytes) {
        final StringBuilder sb = new StringBuilder();
        for (final byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString();
    }
}
