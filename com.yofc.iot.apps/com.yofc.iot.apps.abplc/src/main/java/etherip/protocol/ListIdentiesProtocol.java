/*******************************************************************************
 * Copyright (c) 2017 NETvisor Ltd.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package etherip.protocol;

import etherip.data.Identity;
import io.netty.buffer.ByteBuf;

/**
 * List Identities response decoder
 *
 * @author László Pataki
 */
public class ListIdentiesProtocol extends ProtocolAdapter {

    private Identity[] identities;

    @Override
    public void decode(final ByteBuf buf) {
        // Decode reply to ListIdentity
        final int count = buf.readShortLE();

        this.identities = new Identity[count];
        for (int i = 0; i < count; ++i) {
            final byte[] raw = new byte[22];
            buf.readBytes(raw);

            final GetIdentityProtocol getIdentityProtocol = new GetIdentityProtocol();
            getIdentityProtocol.decode(buf);

            final Identity identity = getIdentityProtocol.getValue();

            this.identities[i] = identity;
        }
    }

    /** @return {@link Service}s supported by device */
    public final Identity[] getIdentities() {
        return this.identities;
    }
}
