package com.yofc.iot.component.impl;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(id = "com.yofc.iot.component.impl.HttpProxyComponent", name = "%name", description = "%description")
@interface HttpProxyComponentCfg {

    @AttributeDefinition(name = "%ip", description = "%ipDesc")
    String ip() default "localhost";

    @AttributeDefinition(name = "%port", description = "%portDesc")
    int port() default 80;

    @AttributeDefinition(name = "%context", description = "%contextDesc", required = false)
    String context() default "";

    @AttributeDefinition(name = "%routeUrl", description = "%routeUrlDesc")
    String routeUrl() default "/*";
}
