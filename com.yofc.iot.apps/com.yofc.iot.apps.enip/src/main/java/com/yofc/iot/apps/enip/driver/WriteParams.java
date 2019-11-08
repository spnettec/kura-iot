/**
 * Copyright (c) 2018 Eurotech and/or its affiliates and others
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 */

package com.yofc.iot.apps.enip.driver;

import java.text.NumberFormat;
import java.util.Map;
import java.util.function.Function;

import org.eclipse.kura.type.DataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yofc.iot.apps.enip.cip.structs.CIPData;
import com.yofc.iot.apps.enip.cip.structs.CIPData.Type;

public class WriteParams {

    private static final Logger logger = LoggerFactory.getLogger(WriteParams.class);
    private CIPData writeValue;
    private String writeTag;

    public WriteParams(final Map<String, Object> channelConfig, final Object value) {
        writeTag = channelConfig.get(EIPChannelDescriptor.DATA_TAG_NAME_ID).toString();
        try {
            final Type valueType = tryExtract(channelConfig,
                    config -> EIPDataType.getDataType((String) config.get(EIPChannelDescriptor.TAG_DATA_TYPE_ID)),
                    "Error while retrieving value type");
            final DataType dataType = tryExtract(channelConfig,
                    config -> DataType.valueOf((String) config.get("+value.type")),
                    "Error while retrieving value type");
            if (valueType == Type.STRUCT_STRING || valueType == Type.STRUCT) {
                this.writeValue = new CIPData(String.valueOf(value));
            } else if (valueType == Type.BITS)
                this.writeValue = new CIPData(valueType, (byte[]) value);
            else if (valueType == Type.BOOL) {
                this.writeValue = new CIPData(valueType);
                boolean isTrue = Boolean.parseBoolean(String.valueOf(value));
                this.writeValue.set(0, (Number) (isTrue ? 1 : 0));
            } else {
                this.writeValue = new CIPData(valueType);
                if (dataType == DataType.BOOLEAN)
                    this.writeValue.set(0, (Number) (Boolean.TRUE.equals(value) ? 1 : 0));
                else
                    this.writeValue.set(0, NumberFormat.getInstance().parse(String.valueOf(value)));
            }

        } catch (Exception e) {
            logger.error("WriteParams error", e);
            this.writeValue = null;
        }
    }

    public CIPData getWriteValue() {
        return this.writeValue;
    }

    public String getWriteTag() {
        return this.writeTag;
    }

    private static <T> T tryExtract(final Map<String, Object> properties, final Function<Map<String, Object>, T> func,
            final String errorMessage) {
        try {
            return func.apply(properties);
        } catch (Exception e) {
            throw new IllegalArgumentException(errorMessage);
        }
    }
}
