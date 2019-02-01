package com.yofc.iot.component.api;

import java.util.List;

import org.eclipse.kura.core.configuration.metatype.Tad;
import org.eclipse.kura.core.configuration.metatype.Tscalar;
import org.eclipse.kura.driver.ChannelDescriptor;
import org.eclipse.kura.util.collection.CollectionUtil;

public final class H2MVStoreChannelDescriptor implements ChannelDescriptor {

    public static final String DATA_TAG_NAME_ID = "data.tag.name";

    /** {@inheritDoc} */
    @Override
    public Object getDescriptor() {
        final List<Tad> elements = CollectionUtil.newArrayList();

        final Tad tagName = new Tad();
        tagName.setName(DATA_TAG_NAME_ID);
        tagName.setId(DATA_TAG_NAME_ID);
        tagName.setDescription("TAG NAME");
        tagName.setType(Tscalar.STRING);
        tagName.setRequired(true);
        tagName.setDefault("");

        elements.add(tagName);

        return elements;
    }

}
