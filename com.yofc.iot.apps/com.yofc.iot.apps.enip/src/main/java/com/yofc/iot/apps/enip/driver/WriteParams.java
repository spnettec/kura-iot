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

import java.util.Map;
import java.util.function.Function;

import com.yofc.iot.apps.enip.cip.structs.CIPData;
import com.yofc.iot.apps.enip.cip.structs.CIPData.Type;

public class WriteParams {

    private CIPData writeValue;
    private String writeTag;

    public WriteParams(final Map<String, Object> channelConfig, final Object value) {
        writeTag = channelConfig.get(EIPChannelDescriptor.DATA_TAG_NAME_ID).toString();
        final EIPDataType valueType = tryExtract(channelConfig,
                config -> EIPDataType.getDataType((String) config.get(EIPChannelDescriptor.TAG_DATA_TYPE_ID)),
                "Error while retrieving value type");
        Type type;
        try {
            switch (valueType) {
            case BOOL:
                type = Type.BOOL;
                break;
            case SINT:
                type = Type.BOOL;
                break;
            case INT:
                type = Type.INT;
                break;
            case DINT:
                type = Type.DINT;
                break;
            case REAL:
                type = Type.REAL;
                break;
            case BITS:
                type = Type.BITS;
                break;
            case STRUCT:
                type = Type.STRUCT;
                break;
            case STRUCT_STRING:
                type = Type.STRUCT_STRING;
                break;
            default:
                throw new IllegalArgumentException("Cannot convert to DataType");
            }
            if (valueType == EIPDataType.STRUCT_STRING || valueType == EIPDataType.STRUCT) {
                this.writeValue = new CIPData(String.valueOf(value));
            } else if (valueType == EIPDataType.BITS)
                this.writeValue = new CIPData(type, (byte[]) value);
            else if (valueType == EIPDataType.BOOL) {
                this.writeValue = new CIPData(type);
                boolean isTrue = Boolean.parseBoolean(String.valueOf(value));
                this.writeValue.set(0, (Number) (isTrue ? 1 : 0));
            } else {
                this.writeValue = new CIPData(type);
                this.writeValue.set(0, (Number) value);
            }

        } catch (Exception e) {
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
