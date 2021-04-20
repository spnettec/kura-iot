/**
 * Copyright (c) 2018 Eurotech and/or its affiliates and others
 *
 * <p>All rights reserved. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.yofc.iot.apps.serial;

import java.util.Map;

public class WriteParams {

  private final String writeValue;
  private final String writeTag;

  public WriteParams(final Map<String, Object> channelConfig, final Object value) {
    writeTag = channelConfig.get(SerialChannelDescriptor.DATA_TAG_NAME_ID).toString();
    this.writeValue = String.valueOf(value);
  }

  public String getWriteValue() {
    return this.writeValue;
  }

  public String getWriteTag() {
    return this.writeTag;
  }
}
