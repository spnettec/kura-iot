package com.yofc.iot.component.impl;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.eclipse.kura.asset.AssetService;
import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.wire.WireComponent;
import org.eclipse.kura.wire.WireEmitter;
import org.eclipse.kura.wire.WireHelperService;
import org.eclipse.kura.wire.WireRecord;
import org.eclipse.kura.wire.WireSupport;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.osgi.service.wireadmin.Producer;
import org.osgi.service.wireadmin.Wire;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yofc.iot.component.api.MqttClientService;
import com.yofc.iot.component.api.ServieTool;
import com.yofc.iot.component.impl.MqttClientSubscribe.MqttClientSubscribeCfg;
import com.yofc.vertx.osgi.api.AssetHelp;
import com.yofc.vertx.osgi.api.Utils;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

@Designate(factory = true, ocd = MqttClientSubscribeCfg.class)
@Component(immediate = true, enabled = true, configurationPolicy = ConfigurationPolicy.REQUIRE, service = {
        WireComponent.class, WireEmitter.class, ConfigurableComponent.class,
        Producer.class }, property = { "kura.ui.service.hide:Boolean=true" })
/*
 * property = {
 * "kura.ui.service.hide:Boolean=true", "output.cardinality.minimum:Integer=0",
 * "output.cardinality.maximum:Integer=0", "output.cardinality.default:Integer=0",
 * "input.cardinality.minimum:Integer=1", "input.cardinality.maximum:Integer=1",
 * "input.cardinality.default:Integer=1" }
 *
 */
public class MqttClientSubscribe implements WireEmitter, ConfigurableComponent {

    private static final Logger logger = LoggerFactory.getLogger(MqttClientSubscribe.class);
    private final ServieTool serviceTool = new ServieTool();
    private String scriptSource = "";
    @Reference(unbind = "unbindWireHelperService")
    private WireHelperService wireHelperService;
    @Reference
    private Vertx vertx;
    private WebClient webClient;
    private WireSupport wireSupport;
    @Reference
    private AssetService assetService;
    private Script script;
    @Reference
    private AssetHelp assetHelp;
    private volatile MqttClientService mqttClientService;
    private MqttClientSubscribeCfg properties;

    private BiConsumer<String, Object> consumer = (topic, msgObj) -> {
        if (msgObj instanceof org.eclipse.paho.client.mqttv3.MqttMessage msgObjv3) {
            handleMqttMessage(topic, msgObjv3.getPayload());
        } else if (msgObj instanceof org.eclipse.paho.mqttv5.common.MqttMessage msgObjv5) {
            handleMqttMessage(topic, msgObjv5.getPayload());
        }
    };

    private Consumer<Void> connectedConsumer = v -> {
        MqttClientSubscribe.this.subscribe();
    };

    private Consumer<Void> disConnectedConsumer = v -> {
        MqttClientSubscribe.this.unSubscribe();
    };

    public void unbindWireHelperService(final WireHelperService wireHelperService) {
        if (this.wireHelperService == wireHelperService) {
            this.wireHelperService = null;
        }
    }

    public void unbindMqttClientService(MqttClientService mqttClientService) {
        if (this.mqttClientService == mqttClientService && mqttClientService != null) {
            this.mqttClientService.removeConnectedLister(connectedConsumer);
            this.mqttClientService.removeDisConnectedLister(disConnectedConsumer);
            this.mqttClientService = null;
        }
    }

    private void subscribe() {
        try {
            if (MqttClientSubscribe.this.mqttClientService != null && MqttClientSubscribe.this.properties != null) {
                MqttClientSubscribe.this.mqttClientService.subscribe(MqttClientSubscribe.this.properties.mqttTopic(),
                        this.properties.qos(), MqttClientSubscribe.this.consumer);
            }
        } catch (Exception e) {
            logger.error("Subscripe topic: {} error", this.properties.mqttTopic(), e);
        }
    }

    private void unSubscribe() {
        try {
            if (MqttClientSubscribe.this.mqttClientService != null && MqttClientSubscribe.this.properties != null) {
                MqttClientSubscribe.this.mqttClientService.unsubscribe(MqttClientSubscribe.this.properties.mqttTopic(),
                        MqttClientSubscribe.this.consumer);
            }
        } catch (Exception e) {
            logger.error("unsubscribe topic: {} error", this.properties.mqttTopic(), e);
        }
    }

    @Reference(name = "MqttClientService", policy = ReferencePolicy.DYNAMIC, cardinality = ReferenceCardinality.OPTIONAL, bind = "bindMqttClientService", unbind = "unbindMqttClientService")
    public void bindMqttClientService(MqttClientService mqttClientService) {
        if (this.mqttClientService != mqttClientService && mqttClientService != null) {
            this.mqttClientService = mqttClientService;
            this.mqttClientService.connectedLister(connectedConsumer);
            this.mqttClientService.disConnectedLister(disConnectedConsumer);
            if (this.mqttClientService.isConnected()) {
                try {
                    this.mqttClientService.subscribe(MqttClientSubscribe.this.properties.mqttTopic(),
                            this.properties.qos(), MqttClientSubscribe.this.consumer);
                } catch (Exception e) {
                    logger.error("Subscripe topic: {} error", this.properties.mqttTopic(), e);
                }
            }
        }
    }

    private void handleMqttMessage(String topic, byte[] payload) {
        Buffer buffer = Buffer.buffer(payload);
        WireRecord wireRecord = Utils.bufferToWireRecord(topic, buffer);
        Object output = null;
        if (!"".equals(this.scriptSource)) {
            try {
                script.setProperty("input", wireRecord);
                WireRecord outputEnvelopeWrapper = Utils.bufferToWireRecord(topic, buffer);
                script.setProperty("output", outputEnvelopeWrapper);
                script.run();
                output = script.getProperty("output");
                if (output == null) {
                    return;
                }

            } catch (Exception e) {
                logger.error("Failured to execute script", e);
            }
        }
        if (output == null) {
            output = wireRecord;
        }
        this.wireSupport.emit(output);

    }

    @SuppressWarnings("unchecked")
    @Activate
    public void activate(final ComponentContext componentContext, MqttClientSubscribeCfg properties) {
        logger.info("Activating Script Filter...");
        this.wireSupport = this.wireHelperService.newWireSupport(this,
                (ServiceReference<WireComponent>) componentContext.getServiceReference());
        updated(properties);

        logger.info("ActivatingScript Filter... Done");
    }

    @Deactivate
    public void deactivate() {
        logger.info("Deactivating Script Filter...");
        serviceTool.ungetServices();
        if (webClient != null) {
            webClient.close();
            webClient = null;
        }
        if (this.mqttClientService != null) {
            this.mqttClientService.removeConnectedLister(connectedConsumer);
            this.mqttClientService.removeDisConnectedLister(disConnectedConsumer);
        }
        logger.info("Deactivating Script Filter... Done");
    }

    @Modified
    public synchronized void updated(final MqttClientSubscribeCfg properties) {
        logger.info("Updating Script Filter...");

        serviceTool.ungetServices();
        if (this.properties != properties) {
            unSubscribe();
            this.properties = properties;
            subscribe();
        }

        scriptSource = properties.script();

        if (!"".equals(scriptSource)) {
            Utils.executeWithTCCLSwitch(() -> {
                if (webClient != null) {
                    webClient.close();
                    webClient = WebClient.create(vertx, new WebClientOptions().setIdleTimeout(60000));
                } else {
                    webClient = WebClient.create(vertx, new WebClientOptions().setIdleTimeout(60000));
                }
            });
            Binding binding = new Binding();
            GroovyShell shell = new GroovyShell(binding);
            binding.setVariable("logger", logger);
            binding.setVariable("webClient", webClient);
            binding.setVariable("assetHelp", assetHelp);
            binding.setVariable("serviceTool", serviceTool);
            binding.setVariable("vertx", vertx);
            try {
                script = shell.parse(scriptSource);
            } catch (Exception e) {
                logger.error("Script parse error:", e);
                scriptSource = null;
                return;
            }
            logger.info("Updating Script Filter... Done");
        }
        if (this.mqttClientService != null) {
            this.mqttClientService.connectedLister(connectedConsumer);
            this.mqttClientService.disConnectedLister(disConnectedConsumer);
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

    @ObjectClassDefinition(id = "com.yofc.iot.component.impl.MqttClientSubscribe", name = "%name", description = "%description", localization = "OSGI-INF/l10n/com.yofc.iot.component.impl.MqttClientSubscribeCfg")
    @interface MqttClientSubscribeCfg {

        @AttributeDefinition(name = "%serviceTarget", cardinality = 0, description = "%serviceTargetDesc", required = false)
        String MqttClientService_target()

        default "(kura.service.pid=changeme)";

        @AttributeDefinition(name = "%script", cardinality = 0, description = "%scriptDesc", required = false)
        String script()

        default "";

        @AttributeDefinition(name = "%mqttTopic", cardinality = 0, description = "%mqttTopicDesc", required = true)
        String mqttTopic()

        default "yofc/iot";

        @AttributeDefinition(name = "%qos", cardinality = 0, description = "%qosDesc", required = true)
        int qos()

        default 0;

        @AttributeDefinition(name = "%retain", description = "%retainDesc")
        boolean retain() default false;
    }
}
