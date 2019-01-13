package com.yofc.example;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(id = "com.yofc.example.ExampleConfigurableComponent", name = "%name", description = "%description")
@interface Config {

    @AttributeDefinition(name = "%somethingname", description = "%somethingdesc")
    String someString() default "Hello World";

    @AttributeDefinition(name = "Enabled", description = "Whether the component is enabled or not")
    boolean enabled() default true;
}
