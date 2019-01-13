package com.yofc.iot.component.impl;

import java.util.Map;

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

import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 *
 * @author heyoulin
 */
@Designate(factory = true, ocd = HttpRestComponentCfg.class)
@Component(immediate = true, enabled = true, configurationPolicy = ConfigurationPolicy.REQUIRE, service = {
        WireComponent.class, WireEmitter.class, ConfigurableComponent.class,
        Producer.class }, property = { "kura.ui.service.hide:Boolean=true" })
public class HttpRestComponent implements WireEmitter, ConfigurableComponent {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpRestComponent.class);
    @Reference
    private EventBus eventBus;
    @Reference
    private Router router;
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
        eventBus.localConsumer("aaa", msg -> {
            LOGGER.info("received message:{}", msg.body());
        });
    }

    @Modified
    protected void updated(final Map<String, Object> properties) {
        dumpProperties(properties);
    }

    private void dumpProperties(final Map<String, Object> properties) {
        String routeString = (String) properties.get("route");
        destroy();
        route = router.route(routeString).handler(this::handleResult);
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
        /*
         * String productID = routingContext.request().getParam("productID");
         * HttpServerResponse response = routingContext.response();
         * if (productID == null) {
         * sendError(400, response);
         * } else {
         * JsonObject product = products.get(productID);
         * if (product == null) {
         * sendError(404, response);
         * } else {
         * response.putHeader("content-type", "application/json").end(product.encode());
         * }
         * }
         */

        HttpServerResponse response = routingContext.response();
        response.putHeader("content-type", "application/json").end(routingContext.request().absoluteURI());
        eventBus.send("aaa", JsonObject.mapFrom(routingContext.request().formAttributes()));
        eventBus.send("aaa", JsonObject.mapFrom(routingContext.request().params()));

    }

    // private void sendError(int statusCode, HttpServerResponse response) {
    // response.setStatusCode(statusCode).end();
    // }

}
