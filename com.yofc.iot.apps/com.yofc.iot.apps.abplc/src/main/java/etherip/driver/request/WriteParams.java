/**
 * Copyright (c) 2018 Eurotech and/or its affiliates and others
 *
 * <p>All rights reserved. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package etherip.driver.request;

import etherip.driver.ABPlcChannelDescriptor;
import etherip.types.CIPData;
import etherip.types.CIPData.Type;
import org.eclipse.kura.type.DataType;

import java.util.Map;

public class WriteParams {

  private final String writeTag;
  private CIPData writeValue;

  public WriteParams(final Map<String, Object> channelConfig, final Object value) {
    writeTag = channelConfig.get(ABPlcChannelDescriptor.DATA_TAG_NAME_ID).toString();
    final DataType valueType =
        Utils.tryExtract(
            channelConfig,
            config -> DataType.valueOf((String) config.get("+value.type")),
            "Error while retrieving value type");
    Type type;
    try {
      switch (valueType) {
        case BOOLEAN:
          type = Type.BOOL;
          break;
        case BYTE_ARRAY:
          type = Type.BITS;
          break;
        case DOUBLE:
          type = Type.REAL;
          break;
        case INTEGER:
          type = Type.INT;
          break;
        case LONG:
          type = Type.DINT;
          break;
        case FLOAT:
          type = Type.REAL;
          break;
        case STRING:
          type = Type.STRUCT;
          break;
        default:
          throw new IllegalArgumentException("Cannot convert to DataType");
      }
      if (valueType == DataType.STRING) {
        this.writeValue = new CIPData(String.valueOf(value));
      } else if (valueType == DataType.BYTE_ARRAY) {
        this.writeValue = new CIPData(type, (byte[]) value);
      } else if (valueType == DataType.BOOLEAN) {
        this.writeValue = new CIPData(type);
        boolean isTrue = Boolean.parseBoolean(String.valueOf(value));
        this.writeValue.set(0, isTrue ? 1 : 0);
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
}
