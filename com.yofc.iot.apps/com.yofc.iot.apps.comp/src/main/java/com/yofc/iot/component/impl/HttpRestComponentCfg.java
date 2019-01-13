package com.yofc.iot.component.impl;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(id = "com.yofc.iot.component.impl.HttpRestComponent", name = "%name", description = "%description")
@interface HttpRestComponentCfg {

    @AttributeDefinition(name = "%route", description = "%routeDesc", required = true)
    String route() default "/demo/*";
}
