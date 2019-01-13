package com.yofc.vertx.osgi;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(id = "com.yofc.vertx.osgi.VertxActivator", name = "%name", description = "%description")
@interface Config {

    @AttributeDefinition(name = "%port", description = "%portdesc")
    int port() default 8088;

    @AttributeDefinition(name = "%enabled", description = "%enabledDesc")
    boolean enabled() default false;
}
