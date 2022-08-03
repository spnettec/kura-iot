package com.yofc.vertx.osgi;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.kura.KuraException;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yofc.vertx.osgi.api.AssetHelp;
import com.yofc.vertx.osgi.api.Utils;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.SockJSBridgeOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;

@Component(immediate = true)
public class VertxActivatorBase {

    private static final Logger logger = LoggerFactory.getLogger(VertxActivatorBase.class);
    private final Set<Route> routes = new HashSet<>();
    private ServiceRegistration<Vertx> vertxRegistration;
    private ServiceRegistration<EventBus> ebRegistration;
    private ServiceRegistration<Router> rtRegistration;
    private Vertx vertx;
    @Reference
    private AssetHelp assetHelp;

    @Activate
    public void start(BundleContext context) {

        this.vertx = Vertx.vertx();

        vertxRegistration = context.registerService(Vertx.class, this.vertx, null);
        logger.info("Vert.x service registered");
        ebRegistration = context.registerService(EventBus.class, vertx.eventBus(), null);
        Router router = Router.router(vertx);
        Set<String> allowedHeaders = new HashSet<>();
        allowedHeaders.add("x-requested-with");
        allowedHeaders.add("Access-Control-Allow-Origin");
        allowedHeaders.add("origin");
        allowedHeaders.add("Content-Type");
        allowedHeaders.add("lang");
        allowedHeaders.add("Authorization");
        allowedHeaders.add("accept");
        allowedHeaders.add("X-PINGARUNER");

        Set<HttpMethod> allowedMethods = new HashSet<>();
        allowedMethods.add(HttpMethod.GET);
        allowedMethods.add(HttpMethod.POST);
        allowedMethods.add(HttpMethod.OPTIONS);
        routes.add(router.route().handler(CorsHandler.create(".*").allowCredentials(true).allowedHeaders(allowedHeaders)
                .allowedMethods(allowedMethods)));
        routes.add(router.route("/static/*").order(100).handler(StaticHandler.create()));
        routes.add(router.post("/api/:method").order(100).handler(BodyHandler.create()).handler(ctx -> {
            String method = ctx.request().getParam("method");
            JsonObject body = null;
            try {
                body = ctx.body().asJsonObject();
            } catch (Exception e) {
                String aaa = ctx.body().asString();
                ctx.response().putHeader("content-type", "text/html; charset=utf-8");
                ctx.response().setStatusCode(400).setStatusMessage("parameter error" + aaa).end();
                logger.error("Rest Body Error", e);
                return;
            }
            String assetPid = body.getString("assetPid");
            switch (method) {
            case "readTags":
                List<String> tagNames = body.getJsonArray("tagNames").stream().map(obj -> String.valueOf(obj))
                        .collect(Collectors.toList());
                try {
                    JsonObject result = assetHelp.readTags(assetPid, tagNames);
                    if (result == null) {
                        throw KuraException.internalError("读取标签错误");
                    }
                    ctx.response().putHeader("content-type", "application/json; charset=utf-8")
                            .end(result.encodePrettily());
                } catch (Exception e) {
                    ctx.response().putHeader("content-type", "text/html; charset=utf-8");
                    ctx.response().setStatusCode(400).setStatusMessage(Utils.utf8ToAscii(e.getMessage())).end();
                    logger.error("Read Tag Error", e);
                }
                break;
            case "writeTags":
                try {
                    final JsonObject tagsObj = new JsonObject();
                    Object tags = body.getValue("tagNames");
                    if (tags instanceof JsonArray) {
                        JsonArray jarray = (JsonArray) tags;
                        jarray.stream().forEach(obj -> {
                            JsonObject jo = (JsonObject) obj;
                            tagsObj.getMap().putAll(jo.getMap());
                        });
                        assetHelp.writeTags(assetPid, tagsObj);

                    } else if (tags instanceof JsonObject) {
                        assetHelp.writeTags(assetPid, (JsonObject) tags);
                    } else {
                        ctx.response().putHeader("content-type", "text/html; charset=utf-8");
                        ctx.response().setStatusCode(400).setStatusMessage("parameter error").end();
                        return;
                    }
                    ctx.response().end((new JsonObject()).encodePrettily());
                } catch (Exception e) {
                    ctx.response().putHeader("content-type", "text/html; charset=utf-8");
                    ctx.response().setStatusCode(400).setStatusMessage(e.getMessage()).end();
                    logger.error("Write Tag Error", e);
                }
                break;
            default:
                ctx.response().putHeader("content-type", "text/html; charset=utf-8");
                ctx.response().setStatusCode(405).setStatusMessage("method not suported").end();
            }
        }));

        SockJSBridgeOptions opts = new SockJSBridgeOptions()
                .addInboundPermitted(new PermittedOptions().setAddressRegex("tagData-.*"))
                .addOutboundPermitted(new PermittedOptions().setAddressRegex("tagData-.*"));
        routes.add(router.route("/bridge/*").subRouter(SockJSHandler.create(vertx).bridge(opts)));
        rtRegistration = context.registerService(Router.class, router, null);
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
