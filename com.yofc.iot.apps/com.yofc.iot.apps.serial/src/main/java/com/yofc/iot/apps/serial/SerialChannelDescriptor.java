package com.yofc.iot.apps.serial;

import org.eclipse.kura.core.configuration.metatype.Tad;
import org.eclipse.kura.core.configuration.metatype.Tscalar;
import org.eclipse.kura.driver.ChannelDescriptor;
import org.eclipse.kura.util.collection.CollectionUtil;

import java.util.List;

public final class SerialChannelDescriptor implements ChannelDescriptor {

  public static final String DATA_TAG_NAME_ID = "data.tag.name";
  public static final String DATA_TAG_READ_LENGTH = "data.tag.read.length";

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

    final Tad tagName1 = new Tad();
    tagName1.setName("%" + DATA_TAG_READ_LENGTH);
    tagName1.setId(DATA_TAG_READ_LENGTH);
    tagName1.setDescription("%" + DATA_TAG_READ_LENGTH + "Desc");
    tagName1.setType(Tscalar.INTEGER);
    tagName1.setRequired(true);
    tagName1.setDefault("10");

    elements.add(tagName);

    elements.add(tagName1);

    return elements;
  }
}
