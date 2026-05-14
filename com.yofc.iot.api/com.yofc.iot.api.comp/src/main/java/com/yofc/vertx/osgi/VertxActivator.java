package com.yofc.vertx.osgi;

import com.yofc.vertx.osgi.VertxActivator.Config;
import com.yofc.vertx.osgi.api.Utils;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import io.vertx.core.ThreadingModel;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import org.eclipse.kura.configuration.ConfigurableComponent;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A bundle activator registering the Vert.x instance and the event bus as OSGi service. */
@Designate(ocd = Config.class)
@Component(immediate = true, configurationPolicy = ConfigurationPolicy.REQUIRE)
public class VertxActivator implements ConfigurableComponent {

    private static final Logger logger = LoggerFactory.getLogger(VertxActivator.class);
    @Reference
    private Vertx vertx;
    @Reference
    private Router router;
    private String deploymentId;

    @Activate
    public void start(BundleContext context, Config properties) {
        if (!properties.enabled()) {
            stop(context);
            return;
        }
        deployServer(properties.port());
    }

    @Modified
    public void update(BundleContext context, Config properties) {
        logger.info("Modified");
        start(context, properties);
    }

    @Deactivate
    public void stop(BundleContext context) {
        if (deploymentId != null) {
            vertx.undeploy(deploymentId);
            deploymentId = null;
        }
    }

    private void deployServer(int port) {
        if (deploymentId != null) {
            String oldDeploymentId = deploymentId;
            deploymentId = null;
            vertx.undeploy(oldDeploymentId).onComplete(ar -> deployServer(port));
            return;
        }

        Utils.executeWithTCCLSwitch(() -> {
            vertx.deployVerticle(() -> new AbstractVerticle() {
                    private HttpServer server;

                    @Override
                    public void start(Promise<Void> startPromise) {
                        server = vertx.createHttpServer();
                        server.requestHandler(router).listen(port).<Void>mapEmpty().onComplete(startPromise);
                    }

                    @Override
                    public void stop(Promise<Void> stopPromise) {
                        if (server != null) {
                            server.close().<Void>mapEmpty().onComplete(stopPromise);
                        } else {
                            stopPromise.complete();
                        }
                    }
                }, new DeploymentOptions().setThreadingModel(ThreadingModel.VIRTUAL_THREAD)).onComplete(ar -> {
                if (ar.succeeded()) {
                    deploymentId = ar.result();
                } else {
                    logger.error("Cannot deploy Vert.x HTTP server", ar.cause());
                }
            });
        });
    }

    @ObjectClassDefinition(id = "com.yofc.vertx.osgi.VertxActivator", name = "%name", description = "%description", localization = "OSGI-INF/l10n/com.yofc.vertx.osgi.Config")
    public @interface Config {

        @AttributeDefinition(name = "%port", description = "%portDesc") int port() default 8088;

        @AttributeDefinition(name = "%enabled", description = "%enabledDesc") boolean enabled() default false;
    }
}
