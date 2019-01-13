package com.yofc.iot.component.impl;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.osgi.service.metatype.annotations.Option;

@ObjectClassDefinition(id = "com.yofc.iot.component.impl.ValueChangeComponent", name = "%name", description = "%description")
@interface ValueChangeComponentCfg {

    @AttributeDefinition(name = "%type", description = "%typeDesc", options = { @Option(label = "and", value = "and"),
            @Option(label = "or", value = "or") }, required = true)
    String type() default "or";

    @AttributeDefinition(name = "%tags", cardinality = -1, description = "%tagsDesc", required = false)
    String[] tags() default "";
}
