/*******************************************************************************
 * Copyright (c) 2017 NETvisor Ltd.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package etherip.protocol;

import etherip.types.CNClassPath;
import io.netty.buffer.ByteBuf;

/**
 * CNClassPath parameter decoder.<br>
 * If {@link CNClassPath} is a parameter of an object in a response then should not be encoded.
 *
 * @author László Pataki
 */
public class GetPhysicalLinkObjectProtocol extends CNClassPath {

  @Override
  public void encode(final ByteBuf buf) {}
}
