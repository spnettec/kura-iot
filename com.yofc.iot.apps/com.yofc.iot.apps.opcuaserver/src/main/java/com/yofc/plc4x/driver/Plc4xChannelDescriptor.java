package com.yofc.plc4x.driver;

import java.util.List;

import org.eclipse.kura.core.configuration.metatype.Tad;
import org.eclipse.kura.core.configuration.metatype.Tscalar;
import org.eclipse.kura.driver.ChannelDescriptor;
import org.eclipse.kura.util.collection.CollectionUtil;

public class Plc4xChannelDescriptor implements ChannelDescriptor {

    public static final String DATA_TAG_ADDR_ID = "data.tag.addr";

    @Override
    public Object getDescriptor() {
        final List<Tad> elements = CollectionUtil.newArrayList();

        final Tad tagAddr = new Tad();
        tagAddr.setName("%" + DATA_TAG_ADDR_ID);
        tagAddr.setId(DATA_TAG_ADDR_ID);
        tagAddr.setDescription("%data.tag.addrDesc");
        tagAddr.setType(Tscalar.STRING);
        tagAddr.setRequired(true);
        tagAddr.setDefault("");

        elements.add(tagAddr);

        return elements;
    }

}
