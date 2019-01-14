package com.yofc.iot.component.impl;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(id = "com.yofc.iot.component.impl.GroovyScriptFilter", name = "%name", description = "%description")
@interface GroovyScriptFilterCfg {

    @AttributeDefinition(name = "%script", cardinality = 0, description = "%scriptDesc", required = true)
    String script() default "def aa=1";
}
