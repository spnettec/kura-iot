package com.yofc.vertx.osgi;

import org.eclipse.kura.configuration.ConfigurableComponent;
import org.h2.tools.Server;
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
    private HttpServer server;
    private Server h2WebServer;

    @Activate
    public void start(BundleContext context, Config properties) {
        if (properties.enabled())
            startRest(properties);
        else
            stopRest();
        if (properties.h2WebEnabled())
            startH2(properties);
        else
            stoph2();
    }

    @Modified
    public void update(BundleContext context, Config properties) {
        logger.info("Modified");
        start(context, properties);
    }

    private void startRest(Config properties) {
        if (server != null)
            stopRest();
        server = vertx.createHttpServer();
        int port = properties.port();
        server.requestHandler(router).listen(port);
    }

    private void startH2(Config properties) {
        try {
            if (h2WebServer != null)
                stoph2();
            h2WebServer = Server
                    .createWebServer("-webAllowOthers", "-webPort", Integer.toString(properties.h2WebPort())).start();
        } catch (Exception e) {
            logger.error("start h2 web console error", e);
        }
    }

    private void stoph2() {
        if (h2WebServer != null) {
            h2WebServer.shutdown();
            h2WebServer = null;
        }
    }

    private void stopRest() {
        if (server != null) {
            server.close();
            server = null;
        }
    }

    @Deactivate
    public void stop(BundleContext context) {
        stopRest();
        stoph2();
    }

    @ObjectClassDefinition(id = "com.yofc.vertx.osgi.VertxActivator", name = "%name", description = "%description", localization = "OSGI-INF/l10n/com.yofc.vertx.osgi.Config")
    @interface Config {

        @AttributeDefinition(name = "%port", description = "%portDesc")
        int port() default 8088;

        @AttributeDefinition(name = "%enabled", description = "%enabledDesc")
        boolean enabled() default false;

        @AttributeDefinition(name = "%h2WebPort", description = "%h2WebPortDesc")
        int h2WebPort() default 8082;

        @AttributeDefinition(name = "%h2WebEnabled", description = "%h2WebEnabledDesc")
        boolean h2WebEnabled() default false;
    }

}
