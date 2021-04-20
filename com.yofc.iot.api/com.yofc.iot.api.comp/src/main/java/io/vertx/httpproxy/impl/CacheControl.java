/*
 * Copyright (c) 2011-2020 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.httpproxy.impl;

/** @author <a href="mailto:julien@julienviet.com">Julien Viet</a> */
public class CacheControl {

  private int maxAge;
  private boolean public_;

  public CacheControl parse(String header) {
    maxAge = -1;
    public_ = false;
    // No regex
    String[] parts = header.split(",");
    for (String part : parts) {
      part = part.trim().toLowerCase();
      switch (part) {
        case "public":
          public_ = true;
          break;
        default:
          if (part.startsWith("max-age=")) {
            maxAge = Integer.parseInt(part.substring(8));
          }
          break;
      }
    }
    return this;
  }

  public int maxAge() {
    return maxAge;
  }

  public boolean isPublic() {
    return public_;
  }
}
