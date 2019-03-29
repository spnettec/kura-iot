package com.yofc.iot.component.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

import com.yofc.iot.component.impl.HttpRestComponent.HttpRestComponentCfg;
import com.yofc.vertx.osgi.api.AssetHelp;
import com.yofc.vertx.osgi.api.BasicAuth;
import com.yofc.vertx.osgi.api.Utils;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.AuthHandler;
import io.vertx.ext.web.handler.BodyHandler;

/**
 *
 * @author heyoulin
 */
@Designate(factory = true, ocd = HttpRestComponentCfg.class)
@Component(immediate = true, enabled = true, configurationPolicy = ConfigurationPolicy.REQUIRE, service = {
        WireComponent.class, WireEmitter.class, ConfigurableComponent.class,
        Producer.class }, property = { "kura.ui.service.hide:Boolean=true" })
public class HttpRestComponent implements WireEmitter, ConfigurableComponent {

    private static final Logger logger = LoggerFactory.getLogger(HttpRestComponent.class);

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

    public void unbindWireHelperService(final WireHelperService wireHelperService) {
        if (this.wireHelperService == wireHelperService) {
            this.wireHelperService = null;
        }
    }

    @SuppressWarnings("unchecked")
    @Activate
    public void active(final ComponentContext ctx, final HttpRestComponentCfg properties) {
        update(properties);
        this.wireSupport = this.wireHelperService.newWireSupport(this,
                (ServiceReference<WireComponent>) ctx.getServiceReference());

    }

    @Modified
    protected void updated(final HttpRestComponentCfg properties) {
        update(properties);
        if (webClient != null) {
            webClient.close();
            webClient = WebClient.create(vertx);
        } else
            webClient = WebClient.create(vertx);

    }

    private void update(final HttpRestComponentCfg properties) {
        String routeString = properties.route();
        String usersString = properties.users();
        usersString = usersString == null ? "" : usersString;
        destroyRoute();
        route = router.route(routeString);
        if (!usersString.equals("")) {
            JsonArray users = parseUsers(usersString);
            AuthHandler basicAuthHandler = BasicAuth.create(users);
            route.handler(basicAuthHandler);
        }
        route.handler(BodyHandler.create()).blockingHandler(this::handleResult, false);
        scriptSource = properties.script();

        if (scriptSource.equals("")) {
            logger.warn("Script source is null");
        } else {
            Binding binding = new Binding();
            GroovyShell shell = new GroovyShell(binding);
            binding.setVariable("logger", logger);
            binding.setVariable("wireSupport", wireSupport);
            binding.setVariable("assetHelp", assetHelp);
            binding.setVariable("vertx", vertx);
            binding.setVariable("webClient", webClient);
            try {
                script = shell.parse(scriptSource);
            } catch (Exception e) {
                logger.error("Script parse error:{}", e);
                scriptSource = null;
            }
        }
    }

    @Deactivate
    public void deactivate(final ComponentContext ctx) {
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

    private void handleResult(RoutingContext routingContext) {
        HttpServerResponse response = routingContext.response();
        if (scriptSource == null) {
            logger.error("Script parse error.Please input correct groovy script");
            response.end("Script parse error.Please input correct groovy script");
            return;
        }
        if (scriptSource.equals("")) {
            response.end("No response exceuted");
            return;
        }
        try {
            script.setProperty("routingContext", routingContext);
            String outputName = "result";
            script.setProperty(outputName, null);
            script.run();
            Object output = script.getProperty(outputName);
            if (output != null) {
                wireSupport.emit(Utils.getWireRecords(Utils.objectToWireRecord(outputName, output)));
            }
        } catch (Exception e) {
            logger.warn("Failed to execute script", e);
            if (!response.ended())
                response.end(e.toString());
        } finally {
            if (!response.ended())
                response.end("No return information");
        }
    }

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

    @ObjectClassDefinition(id = "com.yofc.iot.component.impl.HttpRestComponent", name = "%name", description = "%description", localization = "OSGI-INF/l10n/com.yofc.iot.component.impl.HttpRestComponentCfg")
    @interface HttpRestComponentCfg {

        @AttributeDefinition(name = "%script", cardinality = 0, description = "%scriptDesc", required = true)
        String script() default "def aa=1";

        @AttributeDefinition(name = "%route", description = "%routeDesc", required = true)
        String route() default "/demo/*";

        @AttributeDefinition(name = "%users", cardinality = 0, description = "%usersDesc", required = false)
        String users() default "admin=111,guest=222";

    }

}
