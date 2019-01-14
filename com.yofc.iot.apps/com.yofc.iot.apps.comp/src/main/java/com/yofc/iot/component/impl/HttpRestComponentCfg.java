package com.yofc.iot.component.impl;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(id = "com.yofc.iot.component.impl.HttpRestComponent", name = "%name", description = "%description")
@interface HttpRestComponentCfg {

    @AttributeDefinition(name = "%route", description = "%routeDesc", required = true)
    String route() default "/demo/*";

    @AttributeDefinition(name = "%script", cardinality = 0, description = "%scriptDesc", required = true)
    String script() default "def aa=1";

    @AttributeDefinition(name = "%users", cardinality = 0, description = "%usersDesc", required = false)
    String users() default "admin=111,guest=222";

}
