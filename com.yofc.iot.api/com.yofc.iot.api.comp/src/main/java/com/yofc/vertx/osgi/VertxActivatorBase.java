package com.yofc.vertx.osgi;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.kura.KuraException;
import org.eclipse.kura.asset.AssetService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yofc.vertx.osgi.api.AssetHelp;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.StaticHandler;

/**
 * A bundle activator registering the Vert.x instance and the event bus as OSGi service.
 */
@Component(immediate = true, service = { VertxActivatorBase.class })
public class VertxActivatorBase {

    private static final Logger logger = LoggerFactory.getLogger(VertxActivatorBase.class);
    private ServiceRegistration<Vertx> vertxRegistration;
    private ServiceRegistration<EventBus> ebRegistration;
    private ServiceRegistration<Router> rtRegistration;
    @Reference
    private AssetService assetService;
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
        routes.add(
                router.route("/static/*").handler(StaticHandler.create("webroot", this.getClass().getClassLoader())));
        AssetHelp assetHelp = new AssetHelp(assetService);
        routes.add(router.post("/api/:method").handler(BodyHandler.create()).handler(ctx -> {
            String method = ctx.request().getParam("method");
            JsonObject body = ctx.getBodyAsJson();
            String assetPid = body.getString("assetPid");
            switch (method) {
            case "readTags":
                List<String> tagNames = body.getJsonArray("tagNames").stream().map(obj -> String.valueOf(obj))
                        .collect(Collectors.toList());
                try {
                    JsonObject result = assetHelp.readTags(assetPid, tagNames);
                    ctx.response().putHeader("content-type", "application/json; charset=utf-8")
                            .end(result.encodePrettily());
                } catch (KuraException e) {
                    ctx.response().putHeader("content-type", "text/html; charset=utf-8");
                    ctx.response().setStatusCode(400).setStatusMessage(e.toString()).end();
                    e.printStackTrace();
                }
                break;
            case "writeTags":
                try {
                    assetHelp.writeTags(assetPid, body.getJsonObject("tagNames"));
                    ctx.response().end();
                } catch (KuraException e) {
                    ctx.response().putHeader("content-type", "text/html; charset=utf-8");
                    ctx.response().setStatusCode(400).setStatusMessage(e.toString()).end();
                    e.printStackTrace();
                }
                break;
            default:
                ctx.response().putHeader("content-type", "text/html; charset=utf-8");
                ctx.response().setStatusCode(405).setStatusMessage("method not suported").end();
            }
        }));
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
