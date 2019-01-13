/*******************************************************************************
 * Copyright (c) 2017 NETvisor Ltd.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package etherip.protocol;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import etherip.data.Identity;

/**
 * Identity Object decoder
 *
 * @see CIP_vol2_v1.4: 5-3.2.2
 * @author László Pataki
 */
public class GetIdentityProtocol extends ProtocolAdapter {

    private Identity identity;

    public static final Logger log = LoggerFactory.getLogger(GetIdentityProtocol.class);

    @Override
    public void decode(final ByteBuffer buf, final int available, final StringBuilder oldLog) throws Exception {
        this.identity = new Identity();

        byte[] raw = new byte[2];

        buf.get(raw);
        this.identity.setVendorId(Integer.valueOf(raw[0] & 0xFF) + Integer.valueOf(raw[1] & 0xFF));

        buf.get(raw);
        this.identity.setDeviceType(Integer.valueOf(raw[0] & 0xFF) + Integer.valueOf(raw[1] & 0xFF));

        buf.get(raw);
        this.identity.setProductCode(Integer.valueOf(raw[0] & 0xFF) + Integer.valueOf(raw[1] & 0xFF));

        buf.get(raw);
        this.identity.setRevision(new Integer[] { Integer.valueOf(raw[0] & 0xFF), Integer.valueOf(raw[1] & 0xFF) });

        raw = new byte[2];
        buf.get(raw);
        this.identity.setStatus(String.format("0x%02X", raw[1]) + String.format("%02X", raw[0]));

        raw = new byte[4];
        buf.get(raw);
        this.identity.setSerialNumber(String.format("0x%02X", raw[3]) + String.format("%02X", raw[2])
                + String.format("%02X", raw[1]) + String.format("%02X", raw[0]));

        raw = new byte[1];
        buf.get(raw);
        final int len = raw[0] & 0xFF;

        raw = new byte[len];
        buf.get(raw);
        this.identity.setProductName(new String(Arrays.copyOfRange(raw, 0, raw.length)));

        log.debug("Identity value      : {}", this.identity);

        // Skip remaining bytes
        final int rest = available - 1 - 2 - 2 - 2 - 2 - 2 - 4 - 1 - len;
        if (rest > 0) {
            buf.position(buf.position() + rest);
        }
    }

    /** @return Value read from response */
    public final Identity getValue() {
        return this.identity;
    }
}
