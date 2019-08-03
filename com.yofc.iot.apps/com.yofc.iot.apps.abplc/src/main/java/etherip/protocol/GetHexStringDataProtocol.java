/*******************************************************************************
 * Copyright (c) 2017 NETvisor Ltd.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package etherip.protocol;

import java.nio.ByteBuffer;

/**
 * Decode the cip response to a string in HEX code<br>
 * Use this for example to check CIP response on byte level without debugging.
 *
 * @author László Pataki
 */
public class GetHexStringDataProtocol extends ProtocolAdapter {

    private String value;

    @Override
    public void decode(final ByteBuffer buf) throws Exception {
        final int len = buf.remaining();
        final byte[] raw = new byte[len];
        buf.get(raw);
        StringBuilder stringBuilder = new StringBuilder();

        for (final byte item : raw) {
            stringBuilder.append(String.format("%02X ", item));
        }
        this.value = stringBuilder.toString();

        // Skip remaining bytes
        /*
         * final int rest = available - len;
         * if (rest > 0) {
         * buf.position(buf.position() + rest);
         * }
         */
    }

    public final String getValue() {
        return this.value;
    }
}
