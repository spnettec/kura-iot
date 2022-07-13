package com.yofc.iot.apps.modbus;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.kura.core.configuration.metatype.Tad;
import org.eclipse.kura.core.configuration.metatype.Toption;
import org.eclipse.kura.core.configuration.metatype.Tscalar;
import org.eclipse.kura.driver.ChannelDescriptor;

public final class ModbusChannelDescriptor implements ChannelDescriptor {

    public static final String REGISTER_TYPE = "registerType";
    public static final String SLAVE_ADDR = "slaveAddr";
    public static final String DATA_ADDRESS = "dataAddress";
    public static final String DATA_TAG_READ_LENGTH = "readLength";

    @Override
    public Object getDescriptor() {
        final List<Tad> elements = new ArrayList<>();

        Tad tag = new Tad();
        tag.setName("%" + REGISTER_TYPE);
        tag.setId(REGISTER_TYPE);
        tag.setDescription("%" + REGISTER_TYPE + "Desc");
        tag.setOption(generateDbTypeOption(RegisterType.HODING));
        tag.setOption(generateDbTypeOption(RegisterType.INPUT));
        tag.setOption(generateDbTypeOption(RegisterType.DISCRETE));
        tag.setOption(generateDbTypeOption(RegisterType.COILS));
        tag.setType(Tscalar.STRING);
        tag.setRequired(true);
        tag.setDefault(RegisterType.INPUT.name());
        elements.add(tag);

        tag = new Tad();
        tag.setName("%" + SLAVE_ADDR);
        tag.setId(SLAVE_ADDR);
        tag.setDescription("%" + SLAVE_ADDR + "Desc");
        tag.setType(Tscalar.STRING);
        tag.setRequired(true);
        tag.setDefault("1");
        elements.add(tag);

        tag = new Tad();
        tag.setName("%" + DATA_ADDRESS);
        tag.setId(DATA_ADDRESS);
        tag.setDescription("%" + DATA_ADDRESS + "Desc");
        tag.setType(Tscalar.STRING);
        tag.setRequired(true);
        tag.setDefault("");
        elements.add(tag);

        tag = new Tad();
        tag.setName("%" + DATA_TAG_READ_LENGTH);
        tag.setId(DATA_TAG_READ_LENGTH);
        tag.setDescription("%" + DATA_TAG_READ_LENGTH + "Desc");
        tag.setType(Tscalar.INTEGER);
        tag.setRequired(true);
        tag.setDefault("1");
        elements.add(tag);

        return elements;
    }

    private Toption generateDbTypeOption(RegisterType type) {
        Toption option = new Toption();
        option.setValue(type.name());
        option.setLabel(type.name());
        return option;
    }
}
