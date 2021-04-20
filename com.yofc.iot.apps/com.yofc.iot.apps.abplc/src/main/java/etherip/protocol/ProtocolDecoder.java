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
 * Decode bytes of a protocol
 *
 * @author Kay Kasemir
 */
public interface ProtocolDecoder {

  /**
   * Decode protocol from buffer.
   *
   * <p>Buffer is initially positioned at its start. This method should then read data from the
   * buffer, leaving it positioned at the end of the data handled by this protocol segment.
   *
   * <p>When using this method in a protocol class hierarchy, the 'base' class can decode the header
   * of a protocol, and derived classes can then decode the remaining bytes.
   *
   * @param buf {@link ByteBuffer} from which to decode protocol
   * @param available Number of bytes available for this protocol section (buffer may contain more
   *     follow-on bytes for surrounding protocol wrappers)
   * @param log If not-<code>null</code>, {@link StringBuilder} where protocol detail for log should
   *     be written
   */
  void decode(ByteBuf buf);
}
