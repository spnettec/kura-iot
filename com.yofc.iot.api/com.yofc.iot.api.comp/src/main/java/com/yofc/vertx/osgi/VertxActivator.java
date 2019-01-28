package com.yofc.vertx.osgi;

import org.eclipse.kura.configuration.ConfigurableComponent;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yofc.vertx.osgi.VertxActivator.Config;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;

/**
 * A bundle activator registering the Vert.x instance and the event bus as OSGi service.
 */
@Designate(ocd = Config.class)
@Component(immediate = true, configurationPolicy = ConfigurationPolicy.REQUIRE)
public class VertxActivator implements ConfigurableComponent {

    private static final Logger logger = LoggerFactory.getLogger(VertxActivator.class);
    @Reference
    private Vertx vertx;
    @Reference
    private Router router;
    private volatile HttpServer server;

    @Activate
    public void start(BundleContext context, Config properties) {
        if (!properties.enabled()) {
            stop(context);
            return;
        }
        server = vertx.createHttpServer();
        int port = properties.port();

        server.requestHandler(router).listen(port);
    }

    @Modified
    public void update(BundleContext context, Config properties) {
        logger.info("Modified");
        start(context, properties);
    }

    @Deactivate
    public void stop(BundleContext context) {
        if (server != null) {
            server.close();
            server = null;
        }

    }

    @ObjectClassDefinition(id = "com.yofc.vertx.osgi.VertxActivator", name = "%name", description = "%description", localization = "OSGI-INF/l10n/com.yofc.vertx.osgi.Config")
    @interface Config {

        @AttributeDefinition(name = "%port", description = "%portdesc")
        int port() default 8088;

        @AttributeDefinition(name = "%enabled", description = "%enabledDesc")
        boolean enabled() default false;
    }

}
