/**
 * Copyright (c) 2017, 2018 Eurotech and/or its affiliates
 *
 * <p>All rights reserved. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * <p>Contributors: Eurotech Amit Kumar Mondal (admin@amitinside.com)
 */
package etherip.driver;

import org.eclipse.kura.core.configuration.metatype.Tad;
import org.eclipse.kura.core.configuration.metatype.Tscalar;
import org.eclipse.kura.driver.ChannelDescriptor;
import org.eclipse.kura.util.collection.CollectionUtil;

import java.util.List;

/**
 * S7 PLC specific channel descriptor. The descriptor contains the following attribute definition
 * identifiers.
 *
 * <ul>
 *   <li>area.no denotes the Area Number
 *   <li>offset the offset
 *   <li>byte.count the number of bytes to read
 * </ul>
 */
public final class ABPlcChannelDescriptor implements ChannelDescriptor {

  public static final String DATA_TAG_NAME_ID = "data.tag.name";

  /** {@inheritDoc} */
  @Override
  public Object getDescriptor() {
    final List<Tad> elements = CollectionUtil.newArrayList();

    final Tad tagName = new Tad();
    tagName.setName("%" + DATA_TAG_NAME_ID);
    tagName.setId(DATA_TAG_NAME_ID);
    tagName.setDescription("%" + DATA_TAG_NAME_ID + "Desc");
    tagName.setType(Tscalar.STRING);
    tagName.setRequired(true);
    tagName.setDefault("");

    elements.add(tagName);

    return elements;
  }
}
