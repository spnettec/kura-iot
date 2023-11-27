package com.yofc.iot.component.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.wire.WireComponent;
import org.eclipse.kura.wire.WireEmitter;
import org.eclipse.kura.wire.WireHelperService;
import org.eclipse.kura.wire.WireSupport;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.osgi.service.wireadmin.Producer;
import org.osgi.service.wireadmin.Wire;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yofc.iot.component.api.ServieTool;
import com.yofc.iot.component.impl.HttpRestComponent.HttpRestComponentCfg;
import com.yofc.vertx.osgi.api.AssetHelp;
import com.yofc.vertx.osgi.api.BasicAuth;
import com.yofc.vertx.osgi.api.Utils;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.handler.AuthenticationHandler;
import io.vertx.ext.web.handler.BodyHandler;

/** @author heyoulin */
@Designate(factory = true, ocd = HttpRestComponentCfg.class)
@Component(immediate = true, enabled = true, configurationPolicy = ConfigurationPolicy.REQUIRE, service = {
        WireComponent.class, WireEmitter.class, ConfigurableComponent.class, Producer.class }, property = {
                "kura.ui.service.hide:Boolean=true", "emitter.null.as.wire.envelope:Boolean=false" })
public class HttpRestComponent implements WireEmitter, ConfigurableComponent {

    private static final Logger logger = LoggerFactory.getLogger(HttpRestComponent.class);
    private final ServieTool serviceTool = new ServieTool();
    private String scriptSource;
    @Reference
    private Vertx vertx;
    private WebClient webClient;
    @Reference
    private EventBus eventBus;
    @Reference
    private Router router;
    private Route route;
    @Reference(policy = ReferencePolicy.STATIC, unbind = "unbindWireHelperService")
    private WireHelperService wireHelperService;
    private WireSupport wireSupport;
    private Script script;
    @Reference
    private AssetHelp assetHelp;

    private static JsonArray parseUsers(final String string) {

        if (string == null || string.isEmpty()) {
            return new JsonArray();
        }
        List<JsonObject> objs = new ArrayList<>();
        final String[] reqs = string.split("\\s*,\\s*");
        for (String req : reqs) {
            final String[] toks = req.split("\\=", 2);
            if (toks.length == 2) {
                final String username = toks[0];
                final String[] roleandPassWords = toks[1].split("\\|", 2);

                JsonObject obj = new JsonObject();
                obj.put("username", username);
                obj.put("password", roleandPassWords[0]);
                if (roleandPassWords.length > 1) {
                    final String[] roles = roleandPassWords[1].split("\\s*\\|\\s*");
                    obj.put("roles", new JsonArray(Arrays.asList(roles)));
                }
                objs.add(obj);
            }
        }
        return new JsonArray(objs);
    }

    public void unbindWireHelperService(final WireHelperService wireHelperService) {
        if (this.wireHelperService == wireHelperService) {
            this.wireHelperService = null;
        }
    }

    @SuppressWarnings("unchecked")
    @Activate
    public void active(final ComponentContext ctx, final HttpRestComponentCfg properties) {
        this.wireSupport = this.wireHelperService.newWireSupport(this,
                (ServiceReference<WireComponent>) ctx.getServiceReference());
        updated(properties);
    }

    @Modified
    protected void updated(final HttpRestComponentCfg properties) {
        if (webClient != null) {
            webClient.close();
        }
        Utils.executeWithTCCLSwitch(() -> {
            webClient = WebClient.create(vertx, new WebClientOptions().setIdleTimeout(60000));
        });
        update(properties);
    }

    private void update(final HttpRestComponentCfg properties) {
        serviceTool.ungetServices();
        String routeString = properties.route();
        String usersString = properties.users();
        usersString = usersString == null ? "" : usersString;
        destroyRoute();
        route = router.route(routeString);
        route.handler(BodyHandler.create().setDeleteUploadedFilesOnEnd(true));
        if (!"".equals(usersString)) {
            JsonArray users = parseUsers(usersString);
            AuthenticationHandler basicAuthHandler = BasicAuth.create(users);
            route.handler(basicAuthHandler);
        }
        route.handler(this::handleResult100Continue).handler(this::handleResult)
                .failureHandler(this::handleRequestFailure);
        scriptSource = properties.script();

        if (!"".equals(scriptSource)) {
            Binding binding = new Binding();
            GroovyShell shell = new GroovyShell(binding);
            binding.setVariable("logger", logger);
            binding.setVariable("serviceTool", serviceTool);
            binding.setVariable("wireSupport", wireSupport);
            binding.setVariable("assetHelp", assetHelp);
            binding.setVariable("vertx", vertx);
            binding.setVariable("webClient", webClient);
            if (!"".equals(properties.initScript())) {
                if (script != null && !script.getMetaClass().respondsTo(script, "destroy").isEmpty()) {
                    try {
                        script.invokeMethod("destroy", null);
                    } catch (Exception e) {
                        logger.error("Script destroy method error", e);
                    }
                }
                scriptSource = scriptSource + "\r\n" + properties.initScript();
            }
            try {
                script = shell.parse(scriptSource);
            } catch (Exception e) {
                logger.error("Script parse error", e);
                scriptSource = null;
                return;
            }
            if (!"".equals(properties.initScript()) && !script.getMetaClass().respondsTo(script, "init").isEmpty()) {
                try {
                    script.invokeMethod("init", null);
                } catch (Exception e) {
                    logger.error("Script init method error", e);
                }
            }
        }
    }

    private void handleRequestFailure(RoutingContext failureroutingContext) {
        logger.debug("Matched route {}", failureroutingContext.currentRoute().getPath());
        int code = failureroutingContext.statusCode();
        failureroutingContext.response().setStatusCode(code);
        failureroutingContext.response().end(failureroutingContext.failure() == null ? "Unknow error"
                : failureroutingContext.failure().getMessage());
    }

    @Deactivate
    public void deactivate(final ComponentContext ctx) {
        serviceTool.ungetServices();
        destroyRoute();
        if (webClient != null) {
            webClient.close();
            webClient = null;
        }
    }

    private void destroyRoute() {
        if (route != null) {
            route.remove();
            route = null;
        }
    }

    @Override
    public Object polled(Wire wire) {
        return this.wireSupport.polled(wire);
    }

    @Override
    public void consumersConnected(Wire[] wires) {
        this.wireSupport.consumersConnected(wires);
    }

    private void handleResult100Continue(RoutingContext routingContext) {
        HttpServerRequest request = routingContext.request();
        HttpServerResponse response = routingContext.response();
        String expect = request.getHeader("Expect");
        if ("100-Continue".equalsIgnoreCase(expect)) {
            response.writeContinue();
        }
        routingContext.next();
    }

    private void handleResult(RoutingContext routingContext) {
        HttpServerResponse response = routingContext.response();
        if (scriptSource == null) {
            logger.error("Script parse error.Please input correct groovy script");
            response.end("Script parse error.Please input correct groovy script");
            return;
        }
        if ("".equals(scriptSource)) {
            response.end("No response exceuted");
            return;
        }
        Object output = null;
        try {
            script.setProperty("routingContext", routingContext);
            String outputName = "result";
            script.setProperty(outputName, null);
            script.run();
            output = script.getProperty(outputName);

            if (output != null && !(output instanceof CompletableFuture || output instanceof Promise
                    || output instanceof Future)) {
                wireSupport.emit(output);
            }
            if (output != null) {
                if (output instanceof CompletableFuture) {
                    @SuppressWarnings("unchecked")
                    CompletableFuture<Object> future = (CompletableFuture<Object>) output;
                    future.whenComplete((obj, e) -> {
                        if (e != null) {
                            response.end(e.toString());
                            wireSupport.emit(e.toString());
                        } else {
                            if (obj == null) {
                                response.end();
                                wireSupport.emit("");
                            } else {
                                response.end(obj.toString());
                                wireSupport.emit(obj.toString());
                            }
                        }
                    });
                } else if (output instanceof Promise) {
                    @SuppressWarnings("unchecked")
                    Promise<Object> promise = (Promise<Object>) output;
                    promise.future().onComplete(v -> {
                        if (v.failed()) {
                            response.end(v.cause() == null ? "unknow reson" : v.cause().getMessage());
                            wireSupport.emit(v.cause() == null ? "unknow reson" : v.cause().getMessage());
                        } else {
                            response.end(v.result().toString());
                            wireSupport.emit(v.result().toString());
                        }
                    });

                } else if (output instanceof Future) {
                    @SuppressWarnings("unchecked")
                    Future<Object> future = (Future<Object>) output;
                    future.onComplete(v -> {
                        if (v.failed()) {
                            response.end(v.cause().getMessage());
                            wireSupport.emit(v.cause().getMessage());
                        } else {
                            response.end(v.result().toString());
                            wireSupport.emit(v.result().toString());
                        }
                    });

                } else {
                    response.end(output.toString());
                    wireSupport.emit(output);
                }
            } else if (!response.ended()) {
                response.end("No return information");
                wireSupport.emit("No return information");
            }
        } catch (Exception e) {
            logger.warn("Failed to execute script", e);
            if (!response.ended()) {
                response.end(e.toString());
                wireSupport.emit(e.toString());
            }
        }
    }

    @ObjectClassDefinition(id = "com.yofc.iot.component.impl.HttpRestComponent", name = "%name", description = "%description", localization = "OSGI-INF/l10n/com.yofc.iot.component.impl.HttpRestComponentCfg")
    @interface HttpRestComponentCfg {

        @AttributeDefinition(name = "%script", cardinality = 0, description = "%scriptDesc", required = false)
        String script() default "def aa=1";

        @AttributeDefinition(name = "%initScript", cardinality = 0, description = "%initScriptDesc", required = false)
        String initScript() default "";

        @AttributeDefinition(name = "%route", description = "%routeDesc", required = true)
        String route() default "/demo/*";

        @AttributeDefinition(name = "%users", cardinality = 0, description = "%usersDesc", required = false)
        String users() default "admin=111,guest=222";
    }
}
