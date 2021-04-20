/**
 * Copyright (c) 2018 Eurotech and/or its affiliates and others
 *
 * <p>All rights reserved. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.yofc.iot.apps.enip.driver;

import java.util.Map;

public class ReadParams {

  private final String readTag;

  public ReadParams(final Map<String, Object> channelConfig) {
    readTag = channelConfig.get(EIPChannelDescriptor.DATA_TAG_NAME_ID).toString();
  }

  public String getReadTag() {
    return this.readTag;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((readTag == null) ? 0 : readTag.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    ReadParams other = (ReadParams) obj;
    if (readTag == null) {
      return other.readTag == null;
    } else return readTag.equals(other.readTag);
  }
}
