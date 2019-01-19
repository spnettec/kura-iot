package com.yofc.vertx.osgi;

import java.util.HashSet;
import java.util.Set;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.CorsHandler;

/**
 * A bundle activator registering the Vert.x instance and the event bus as OSGi service.
 */
@Component(immediate = true, service = { VertxActivatorBase.class })
public class VertxActivatorBase {

    private static final Logger logger = LoggerFactory.getLogger(VertxActivatorBase.class);
    private ServiceRegistration<Vertx> vertxRegistration;
    private ServiceRegistration<EventBus> ebRegistration;
    private ServiceRegistration<Router> rtRegistration;

    private volatile Vertx vertx;
    private final Set<Route> routes = new HashSet<>();

    @Activate
    public void start(BundleContext context) {

        this.vertx = Vertx.vertx();

        vertxRegistration = context.registerService(Vertx.class, this.vertx, null);
        logger.info("Vert.x service registered");
        ebRegistration = context.registerService(EventBus.class, vertx.eventBus(), null);
        Router router = Router.router(vertx);
        routes.add(router.route()
                .handler(CorsHandler.create("*").allowedMethod(HttpMethod.GET).allowedMethod(HttpMethod.POST)
                        .allowedMethod(HttpMethod.DELETE).allowedMethod(HttpMethod.PUT)
                        .allowedMethod(HttpMethod.OPTIONS).allowedHeader("Content-Type")));
        rtRegistration = context.registerService(Router.class, router, null);
        logger.info("Vert.x Event Bus service registered");
    }

    @Deactivate
    public void stop(BundleContext context) {
        if (vertxRegistration != null) {
            vertxRegistration.unregister();
            vertxRegistration = null;
        }
        if (ebRegistration != null) {
            ebRegistration.unregister();
            ebRegistration = null;
        }
        if (rtRegistration != null) {
            rtRegistration.unregister();
            rtRegistration = null;
        }
        routes.forEach(Route::remove);
        vertx.close();
        vertx = null;
    }
}
