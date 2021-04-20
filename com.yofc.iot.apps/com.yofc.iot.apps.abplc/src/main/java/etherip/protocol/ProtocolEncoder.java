/*******************************************************************************
 * Copyright (c) 2012 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package etherip.protocol;

import io.netty.buffer.ByteBuf;

import java.nio.ByteBuffer;

/**
 * Encode bytes of a protocol
 *
 * @author Kay Kasemir
 */
public interface ProtocolEncoder {

  /**
   * Encode protocol into the buffer.
   *
   * <p>Buffer must be positioned where the encoding should start. This method then advances the
   * buffer position as it adds bytes.
   *
   * <p>Several <code>encode</code> calls can be used to append more and more content to the buffer.
   * Finally, the buffer typically needs to be 'flipped' for writing the accumulated content.
   *
   * @param buf {@link ByteBuffer} where protocol should be encoded
   * @param log If not-<code>null</code>, {@link StringBuilder} where protocol detail for log should
   *     be written
   */
  void encode(ByteBuf buf);
}
