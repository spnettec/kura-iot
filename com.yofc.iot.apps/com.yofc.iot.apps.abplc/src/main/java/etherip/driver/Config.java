package etherip.driver;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(id = "etherip.driver.ABPlcDriver", name = "%name", description = "%description")
@interface Config {

    @AttributeDefinition(name = "%hostIp", description = "%hostIpDesc")
    String hostIp() default "localhost";

    @AttributeDefinition(name = "%port", description = "%portDesc")
    int port() default 44818;

    @AttributeDefinition(name = "%slot", description = "%slotDesc")
    int slot() default 0;

    @AttributeDefinition(name = "%maxRequestItems", description = "%maxRequestItemsDesc")
    int maxRequestItems() default 100;
}
