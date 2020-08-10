package com.yofc.iot.component.impl;

import java.util.List;

import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.wire.WireComponent;
import org.eclipse.kura.wire.WireEmitter;
import org.eclipse.kura.wire.WireEnvelope;
import org.eclipse.kura.wire.WireHelperService;
import org.eclipse.kura.wire.graph.EmitterPort;
import org.eclipse.kura.wire.graph.MultiportWireSupport;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.ComponentServiceObjects;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferenceScope;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.osgi.service.wireadmin.Producer;
import org.osgi.service.wireadmin.Wire;

import com.yofc.iot.component.impl.HttpProxyComponent.HttpProxyComponentCfg;
import com.yofc.vertx.osgi.api.ProxyRoute;
import com.yofc.vertx.osgi.api.Utils;

/**
 *
 * @author heyoulin
 *         <property name="output.cardinality.minimum" type="Integer" value="2"/>
 *         <property name="output.cardinality.maximum" type="Integer" value="2"/>
 *         <property name="output.cardinality.default" type="Integer" value="2"/>
 *         <property name="input.port.names" type="String" value="0=if"/>
 *         <property name="output.port.names" type="String">0=then
 *         1=else
 *         </property>emitter.port.count
 *         <property name="input.cardinality.minimum" type="Integer" value="1"/>
 *         <property name="input.cardinality.maximum" type="Integer" value="1"/>
 *         <property name="input.cardinality.default" type="Integer" value="1"/>
 * 
 *         wireadmin.producer.pid
 *         wireadmin.consumer.pid
 *         service.pid
 */
@Designate(factory = true, ocd = HttpProxyComponentCfg.class)
@Component(immediate = true, enabled = true, configurationPolicy = ConfigurationPolicy.REQUIRE, service = {
        WireComponent.class, WireEmitter.class, ConfigurableComponent.class, Producer.class }, property = {
                "kura.ui.service.hide:Boolean=true", "output.cardinality.minimum:Integer=2",
                "output.cardinality.maximum:Integer=2", "output.cardinality.default:Integer=2",
                "input.cardinality.minimum:Integer=0", "input.cardinality.maximum:Integer=0",
                "input.cardinality.default:Integer=0" })
public class HttpProxyComponent implements WireEmitter, ConfigurableComponent {

    @Reference(scope = ReferenceScope.PROTOTYPE_REQUIRED)
    private ComponentServiceObjects<ProxyRoute> proxyRouteRef;
    private ProxyRoute proxyRoute;
    @Reference(policy = ReferencePolicy.STATIC, unbind = "unbindWireHelperService")
    private WireHelperService wireHelperService;
    private MultiportWireSupport wireSupport;
    private EmitterPort prePort;
    private EmitterPort postPort;

    public void unbindWireHelperService(final WireHelperService wireHelperService) {
        if (this.wireHelperService == wireHelperService) {
            this.wireHelperService = null;
        }
    }

    @SuppressWarnings("unchecked")
    @Activate
    public void active(final ComponentContext ctx, HttpProxyComponentCfg properties) {
        proxyRoute = proxyRouteRef.getService();
        dumpProperties(properties);
        this.wireSupport = (MultiportWireSupport) this.wireHelperService.newWireSupport(this,
                (ServiceReference<WireComponent>) ctx.getServiceReference());
        final List<EmitterPort> emitterPorts = this.wireSupport.getEmitterPorts();
        this.prePort = emitterPorts.get(0);
        this.postPort = emitterPorts.get(1);
    }

    @Modified
    protected void updated(final HttpProxyComponentCfg properties) {
        dumpProperties(properties);
    }

    private void dumpProperties(final HttpProxyComponentCfg properties) {
        String ip = properties.ip();
        int port = properties.port();
        long timeout = properties.timeout();
        boolean isSSL = properties.isSSL();
        int order = properties.order();
        String routeContext = properties.routeUrl();
        String proxyContext = properties.context();

        proxyRoute.configure(routeContext, proxyContext, order, isSSL, ip, port, timeout);
        proxyRoute.addPreListener((context, proxyReq, buffer) -> {
            if (prePort.listConnectedWires().isEmpty())
                return null;
            final WireEnvelope outputEnvelope = this.wireSupport
                    .createWireEnvelope(Utils.bufferToWireRecords("paras", buffer));
            prePort.emit(outputEnvelope);
            return null;
        });
        proxyRoute.addPostListener((context, serverResponse, buffer) -> {
            if (postPort.listConnectedWires().isEmpty())
                return null;
            final WireEnvelope outputEnvelope = this.wireSupport
                    .createWireEnvelope(Utils.bufferToWireRecords("results", buffer));
            postPort.emit(outputEnvelope);
            return null;
        });

    }

    @Deactivate
    public void deactivate(final ComponentContext ctx) {
        proxyRouteRef.ungetService(proxyRoute);
        proxyRoute = null;
    }

    @Override
    public Object polled(Wire wire) {
        return this.wireSupport.polled(wire);
    }

    @Override
    public void consumersConnected(Wire[] wires) {
        this.wireSupport.consumersConnected(wires);
    }

    @ObjectClassDefinition(id = "com.yofc.iot.component.impl.HttpProxyComponent", name = "%name", description = "%description", localization = "OSGI-INF/l10n/com.yofc.iot.component.impl.HttpProxyComponentCfg")
    @interface HttpProxyComponentCfg {

        @AttributeDefinition(name = "%ip", description = "%ipDesc")
        String ip() default "localhost";

        @AttributeDefinition(name = "%port", description = "%portDesc")
        int port() default 80;

        @AttributeDefinition(name = "%context", description = "%contextDesc", required = false)
        String context() default "";

        @AttributeDefinition(name = "%routeUrl", description = "%routeUrlDesc")
        String routeUrl() default "/*";

        @AttributeDefinition(name = "%ssl", description = "%sslDesc")
        boolean isSSL() default false;

        @AttributeDefinition(name = "%order", description = "%orderDesc")
        int order() default -1;

        @AttributeDefinition(name = "%timeout", description = "%timeoutDesc")
        long timeout() default 2000L;
    }

}
