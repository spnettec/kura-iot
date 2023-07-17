package com.yofc.plc4x.driver;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.kura.core.configuration.metatype.Tad;
import org.eclipse.kura.core.configuration.metatype.Tscalar;
import org.eclipse.kura.driver.ChannelDescriptor;

public class Plc4xChannelDescriptor implements ChannelDescriptor {

    public static final String DATA_TAG_ADDR_ID = "data.tag.addr";
    public static final String DATA_TAG_ADDR_NAME = "data.tag.name";
    public static final String LISTEN_SAMPLING_INTERVAL_PROP_NAME = "listen.sampling.interval";
    private static final String LISTEN_SAMPLING_INTERVAL_DEFAULT = "1000";

    @Override
    public Object getDescriptor() {
        final List<Tad> elements = new ArrayList<>();

        final Tad samplingInterval = new Tad();
        samplingInterval.setName("%" + LISTEN_SAMPLING_INTERVAL_PROP_NAME);
        samplingInterval.setId(LISTEN_SAMPLING_INTERVAL_PROP_NAME);
        samplingInterval.setDescription("%" + LISTEN_SAMPLING_INTERVAL_PROP_NAME + "Desc");
        samplingInterval.setType(Tscalar.LONG);
        samplingInterval.setRequired(true);
        samplingInterval.setDefault(LISTEN_SAMPLING_INTERVAL_DEFAULT);

        elements.add(samplingInterval);

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
