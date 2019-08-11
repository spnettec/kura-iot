/*******************************************************************************
 * Copyright (c) 2017 NETvisor Ltd.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package etherip.protocol;

import io.netty.buffer.ByteBuf;

//TODO Implement CIP data types according to CPI_Vol_3.3: Appendix_C_Section_C-6.1.
/**
 * Decode 4 byte length attributes.<br>
 *
 * @see CPI_Vol_3.3: Appendix_C_Section_C-6.1.
 * @author László Pataki
 */
public class GetIntAttributeProtocol extends ProtocolAdapter {

    private int value;

    /** {@inheritDoc} */
    @Override
    public void decode(final ByteBuf buf) {
        this.value = buf.readIntLE();
    }

    /** @return Value read from response */
    public final int getValue() {
        return this.value;
    }
}
