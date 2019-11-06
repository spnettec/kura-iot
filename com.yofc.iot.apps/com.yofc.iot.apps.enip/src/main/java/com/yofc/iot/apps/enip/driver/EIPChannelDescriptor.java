
package com.yofc.iot.apps.enip.driver;

import java.util.List;

import org.eclipse.kura.core.configuration.metatype.Tad;
import org.eclipse.kura.core.configuration.metatype.Toption;
import org.eclipse.kura.core.configuration.metatype.Tscalar;
import org.eclipse.kura.driver.ChannelDescriptor;
import org.eclipse.kura.util.collection.CollectionUtil;

public final class EIPChannelDescriptor implements ChannelDescriptor {

    public static final String DATA_TAG_NAME_ID = "data.tag.name";
    public static final String TAG_DATA_TYPE_ID = "tag.data.type";

    /** {@inheritDoc} */
    @Override
    public Object getDescriptor() {
        final List<Tad> elements = CollectionUtil.newArrayList();
        final Tad tagDataType = new Tad();
        tagDataType.setId(TAG_DATA_TYPE_ID);
        tagDataType.setName("%" + TAG_DATA_TYPE_ID);
        tagDataType.setDescription("%" + TAG_DATA_TYPE_ID + "Desc");
        tagDataType.setType(Tscalar.STRING);
        tagDataType.setRequired(true);
        tagDataType.setDefault("");
        tagDataType.setOption(generateDbTypeOption(EIPDataType.BITS));
        tagDataType.setOption(generateDbTypeOption(EIPDataType.BOOL));
        tagDataType.setOption(generateDbTypeOption(EIPDataType.DINT));
        tagDataType.setOption(generateDbTypeOption(EIPDataType.INT));
        tagDataType.setOption(generateDbTypeOption(EIPDataType.REAL));
        tagDataType.setOption(generateDbTypeOption(EIPDataType.SINT));
        tagDataType.setOption(generateDbTypeOption(EIPDataType.STRUCT));
        tagDataType.setOption(generateDbTypeOption(EIPDataType.STRUCT_STRING));

        elements.add(tagDataType);
        final Tad tagName = new Tad();
        tagName.setId(DATA_TAG_NAME_ID);
        tagName.setName("%" + DATA_TAG_NAME_ID);
        tagName.setDescription("%" + DATA_TAG_NAME_ID + "Desc");
        tagName.setType(Tscalar.STRING);
        tagName.setRequired(true);
        tagName.setDefault("");

        elements.add(tagName);

        return elements;
    }

    private Toption generateDbTypeOption(EIPDataType type) {
        Toption option = new Toption();
        option.setValue(type.name());
        option.setLabel(type.name());
        return option;
    }
}
