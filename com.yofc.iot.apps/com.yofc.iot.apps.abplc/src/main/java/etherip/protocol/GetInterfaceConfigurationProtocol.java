/*******************************************************************************
 * Copyright (c) 2017 NETvisor Ltd.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package etherip.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import etherip.data.InterfaceConfiguration;

/**
 * Interface Configuration decoder.
 *
 * @author László Pataki
 */
public class GetInterfaceConfigurationProtocol extends ProtocolAdapter {

    private InterfaceConfiguration interfaceConfiguration;

    public static final Logger log = LoggerFactory.getLogger(GetInterfaceConfigurationProtocol.class);

    private int responseSize;

    public GetInterfaceConfigurationProtocol() {
        this.responseSize = 0;
    }

    @Override
    public void decode(final ByteBuffer buf) {
        this.responseSize = buf.position();
        this.interfaceConfiguration = new InterfaceConfiguration();

        byte[] raw = new byte[4];

        buf.get(raw);
        this.interfaceConfiguration.setIpAddress(Integer.valueOf(raw[3] & 0xFF) + "." + Integer.valueOf(raw[2] & 0xFF)
                + "." + Integer.valueOf(raw[1] & 0xFF) + "." + Integer.valueOf(raw[0] & 0xFF));

        buf.get(raw);
        this.interfaceConfiguration.setSubnetMask(Integer.valueOf(raw[3] & 0xFF) + "." + Integer.valueOf(raw[2] & 0xFF)
                + "." + Integer.valueOf(raw[1] & 0xFF) + "." + Integer.valueOf(raw[0] & 0xFF));

        buf.get(raw);
        this.interfaceConfiguration.setGateway(Integer.valueOf(raw[3] & 0xFF) + "." + Integer.valueOf(raw[2] & 0xFF)
                + "." + Integer.valueOf(raw[1] & 0xFF) + "." + Integer.valueOf(raw[0] & 0xFF));

        buf.get(raw);
        this.interfaceConfiguration.setNameServer(Integer.valueOf(raw[3] & 0xFF) + "." + Integer.valueOf(raw[2] & 0xFF)
                + "." + Integer.valueOf(raw[1] & 0xFF) + "." + Integer.valueOf(raw[0] & 0xFF));

        buf.get(raw);
        this.interfaceConfiguration.setNameServer2(Integer.valueOf(raw[3] & 0xFF) + "." + Integer.valueOf(raw[2] & 0xFF)
                + "." + Integer.valueOf(raw[1] & 0xFF) + "." + Integer.valueOf(raw[0] & 0xFF));

        // raw = new byte[2];
        // short domainNameLength = 0;

        short domainNameLength = buf.getShort();
        domainNameLength = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).getShort();
        if (domainNameLength % 2 != 0) {
            domainNameLength++;
        }
        raw = new byte[domainNameLength];
        buf.get(raw);
        this.interfaceConfiguration.setDomainName(new String(raw));

        this.responseSize = buf.position() - this.responseSize;

        log.info("InterfaceConfiguration value      : {}", this.interfaceConfiguration);
    }

    /** @return Value read from response */
    public final InterfaceConfiguration getValue() {
        return this.interfaceConfiguration;
    }

    @Override
    public int getResponseSize() {
        return this.responseSize;
    }
}
