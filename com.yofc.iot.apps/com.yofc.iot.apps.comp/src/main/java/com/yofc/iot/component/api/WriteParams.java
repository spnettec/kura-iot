/**
 * Copyright (c) 2018 Eurotech and/or its affiliates and others
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 */

package com.yofc.iot.component.api;

import java.util.Map;

public class WriteParams {

    private Object writeValue;
    private String writeTag;

    public WriteParams(final Map<String, Object> channelConfig, final Object value) {
        writeTag = channelConfig.get(H2MVStoreChannelDescriptor.DATA_TAG_NAME_ID).toString();

        this.writeValue = value;
    }

    public Object getWriteValue() {
        return this.writeValue;
    }

    public String getWriteTag() {
        return this.writeTag;
    }
}
