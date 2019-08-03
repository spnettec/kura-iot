/*******************************************************************************
 * Copyright (c) 2012 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package etherip.protocol;

import java.nio.ByteBuffer;

import etherip.util.Hexdump;

/**
 * Protocol body for listing services that the device supports
 *
 * @author Kay Kasemir
 */
public class ListServicesProtocol extends ProtocolAdapter {

    private Service[] services;

    @Override
    public void decode(final ByteBuffer buf, final int available) throws Exception {
        // Decode reply to ListServices
        final int count = buf.getShort();

        this.services = new Service[count];
        for (int i = 0; i < count; ++i) {
            final short type = buf.getShort();
            final short length = buf.getShort();
            final short version = buf.getShort();
            final short flags = buf.getShort();
            final byte[] name = new byte[length - 4];
            buf.get(name);

            final Service service = new Service(type, length, version, flags, name);

            this.services[i] = service;
        }
    }

    /** @return {@link Service}s supported by device */
    public final Service[] getServices() {
        return this.services;
    }

    /** One Service that the ListServices command obtains from the device */
    public static class Service {

        private final short type;
        private final short length;
        private final short version;
        private final short flags;
        private final String name;

        public Service(final short type, final short length, final short version, final short flags,
                final byte[] name) {
            this.type = type;
            this.length = length;
            this.version = version;
            this.flags = flags;
            this.name = Hexdump.escapeChars(name);
        }

        public String getName() {
            return this.name;
        }

        @Override
        public String toString() {
            final StringBuilder msg = new StringBuilder();
            msg.append(String.format("UINT type       : 0x%04X%n", this.type));
            msg.append("UINT length     : ").append(this.length).append("\n");
            msg.append(String.format("UINT version    : 0x%04X%n", this.version));
            msg.append(String.format("UINT flags      : 0x%04X%n", this.flags));
            msg.append("UINT name[]     : ").append(this.name);
            return msg.toString();
        }
    }
}
