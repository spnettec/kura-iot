package com.yofc.iot.component.impl;

import java.util.List;
import java.util.Map;

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
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.wireadmin.Producer;
import org.osgi.service.wireadmin.Wire;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yofc.iot.component.util.Utils;
import com.yofc.vertx.osgi.api.ProxyRoute;

import io.vertx.ext.web.Router;

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

    private static final Logger logger = LoggerFactory.getLogger(HttpProxyComponent.class);
    @Reference
    private Router router;
    @Reference(scope = ReferenceScope.PROTOTYPE_REQUIRED)
    private ComponentServiceObjects<ProxyRoute> proxyRouteRef;
    private ProxyRoute proxyRoute;
    @Reference(policy = ReferencePolicy.STATIC, unbind = "unbindWireHelperService")
    private volatile WireHelperService wireHelperService;
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
    public void active(final ComponentContext ctx, final Map<String, Object> properties) {
        proxyRoute = proxyRouteRef.getService();
        dumpProperties(properties);
        this.wireSupport = (MultiportWireSupport) this.wireHelperService.newWireSupport(this,
                (ServiceReference<WireComponent>) ctx.getServiceReference());
        final List<EmitterPort> emitterPorts = this.wireSupport.getEmitterPorts();
        this.prePort = emitterPorts.get(0);
        this.postPort = emitterPorts.get(1);
    }

    @Modified
    protected void updated(final Map<String, Object> properties) {
        dumpProperties(properties);
    }

    private void dumpProperties(final Map<String, Object> properties) {
        String ip = (String) properties.get("ip");
        int port = (int) properties.get("port");
        String routeContext = (String) properties.get("routeUrl");
        String proxyContext = (String) properties.get("context");

        proxyRoute.configure(routeContext, proxyContext, -1, false, ip, port, 2000L);
        proxyRoute.addPreListener((context, proxyReq, buffer) -> {
            final WireEnvelope outputEnvelope = this.wireSupport
                    .createWireEnvelope(Utils.bufferToWireRecords("paras", buffer));
            prePort.emit(outputEnvelope);
            return null;
        });
        proxyRoute.addPostListener((context, serverResponse, buffer) -> {
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

}
