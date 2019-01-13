package com.yofc.vertx.osgi;

import java.util.Map;

import org.eclipse.kura.configuration.ConfigurableComponent;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;

/**
 * A bundle activator registering the Vert.x instance and the event bus as OSGi service.
 */
@Designate(ocd = Config.class)
@Component(immediate = true, configurationPolicy = ConfigurationPolicy.REQUIRE)
public class VertxActivator implements ConfigurableComponent {

    private static final Logger LOGGER = LoggerFactory.getLogger(VertxActivator.class);
    @Reference
    private Vertx vertx;
    @Reference
    private Router router;
    private volatile HttpServer server;

    @Activate
    public void start(BundleContext context, Map<String, Object> properties) {
        if (!Boolean.TRUE.equals(properties.get("enabled"))) {
            stop(context);
            return;
        }
        server = vertx.createHttpServer();
        Object portStr = properties.get("port");
        portStr = portStr == null ? 8088 : portStr;

        Integer port = Integer.parseInt(String.valueOf(portStr));
        server.requestHandler(router).listen(port);
    }

    @Modified
    public void update(BundleContext context, Map<String, Object> properties) {
        LOGGER.info("Modified");
        start(context, properties);
    }

    @Deactivate
    public void stop(BundleContext context) {
        if (server != null) {
            server.close();
            server = null;
        }

    }
}
