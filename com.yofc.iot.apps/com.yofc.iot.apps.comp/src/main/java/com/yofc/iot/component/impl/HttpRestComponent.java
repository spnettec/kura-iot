package com.yofc.iot.component.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.eclipse.kura.asset.AssetService;
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
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.wireadmin.Producer;
import org.osgi.service.wireadmin.Wire;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yofc.vertx.osgi.api.BasicAuth;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.AuthHandler;

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

    private static final String SCRIPT_PROPERTY_KEY = "script";
    private String scriptSource;

    @Reference
    private EventBus eventBus;
    @Reference
    private Router router;
    @Reference
    private AssetService assetService;
    private Route route;
    @Reference(policy = ReferencePolicy.STATIC, unbind = "unbindWireHelperService")
    private volatile WireHelperService wireHelperService;
    private WireSupport wireSupport;

    public void unbindWireHelperService(final WireHelperService wireHelperService) {
        if (this.wireHelperService == wireHelperService) {
            this.wireHelperService = null;
        }
    }

    @SuppressWarnings("unchecked")
    @Activate
    public void active(final ComponentContext ctx, final Map<String, Object> properties) {
        dumpProperties(properties);
        this.wireSupport = this.wireHelperService.newWireSupport(this,
                (ServiceReference<WireComponent>) ctx.getServiceReference());
    }

    @Modified
    protected void updated(final Map<String, Object> properties) {
        dumpProperties(properties);
    }

    private void dumpProperties(final Map<String, Object> properties) {
        String routeString = (String) properties.get("route");
        String usersString = (String) properties.get("users");
        usersString = usersString == null ? "" : usersString;
        destroy();
        route = router.route(routeString);
        if (!usersString.equals("")) {
            JsonArray users = parseUsers((String) properties.get("users"));
            AuthHandler basicAuthHandler = BasicAuth.create(users);
            route.handler(basicAuthHandler);
        }
        route.handler(this::handleResult);
        scriptSource = (String) properties.get(SCRIPT_PROPERTY_KEY);

        if (scriptSource == null) {
            logger.warn("Script source is null");
        }

    }

    @Deactivate
    public void deactivate(final ComponentContext ctx) {
        destroy();
    }

    private void destroy() {
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
        response.putHeader("content-type", "application/json");
        if (scriptSource == null) {
            response.end("no response exceuted");
            return;
        }
        Binding binding = new Binding();
        try {
            binding.setVariable("logger", logger);
            binding.setVariable("wireSupport", wireSupport);
            binding.setVariable("routingContext", routingContext);
            binding.setVariable("assetService", assetService);
            GroovyShell shell = new GroovyShell(binding);
            shell.evaluate(this.scriptSource);
        } catch (Exception e) {
            logger.warn("Failed to execute script", e);
            if (!response.ended())
                response.end(e.toString());
        } finally {
            if (!response.ended())
                response.end("ok");
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
}
