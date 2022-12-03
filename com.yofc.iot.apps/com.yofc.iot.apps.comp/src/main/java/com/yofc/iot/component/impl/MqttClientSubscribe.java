package com.yofc.iot.component.impl;

import java.util.List;
import java.util.Map;

import org.eclipse.kura.KuraException;
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

import com.alibaba.fastjson2.JSON;
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
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;

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
    @Reference(name = "MqttClientService", policy = ReferencePolicy.DYNAMIC, cardinality = ReferenceCardinality.OPTIONAL, unbind = "unbindMqttClientService")
    private volatile MqttClientService mqttClientService;

    private MqttClientSubscribeCfg properties;

    public void unbindWireHelperService(final WireHelperService wireHelperService) {
        if (this.wireHelperService == wireHelperService) {
            this.wireHelperService = null;
        }
    }

    public void unbindMqttClientService(MqttClientService mqttClientService) {
        if (this.mqttClientService == mqttClientService) {
            this.mqttClientService = null;
        }
    }

    private void subscribe() {
        if (this.mqttClientService == null) {
            logger.error("mqttClientService is not ready");
            return;
        }
        if (this.mqttClientService.isConnected()) {
            try {

                this.mqttClientService.subscribe(this.properties.mqttTopic(), this.properties.qos());
            } catch (Exception e) {
                logger.error("Subscripe topic: {} error", this.properties.mqttTopic(), e);
            }
            this.mqttClientService.messageArrived((topic, msgObj) -> {
                if (msgObj instanceof org.eclipse.paho.client.mqttv3.MqttMessage) {
                    handleMqttMassage(topic, ((org.eclipse.paho.client.mqttv3.MqttMessage) msgObj).getPayload());
                } else if (msgObj instanceof org.eclipse.paho.mqttv5.common.MqttMessage) {
                    handleMqttMassage(topic, ((org.eclipse.paho.mqttv5.common.MqttMessage) msgObj).getPayload());
                }
            });
        } else {
            logger.error("mqttClientService is not connected!");
        }
    }

    private void handleMqttMassage(String topic, byte[] payload) {
        Buffer buffer = Buffer.buffer(payload);
        List<WireRecord> records = Utils.bufferToWireRecords(topic, buffer);
        if (!"".equals(this.scriptSource)) {
            try {
                script.setProperty("input", records);
                List<WireRecord> outputEnvelopeWrapper = Utils.bufferToWireRecords(topic, buffer);
                script.setProperty("output", outputEnvelopeWrapper);
                script.run();
                Object output = script.getProperty("output");
                if (output == null) {
                    return;
                }
                if (output instanceof List) {
                    List<?> outputList = (List<?>) output;
                    if (outputList.isEmpty()) {
                        return;
                    }
                    if (outputList.get(0) instanceof WireRecord) {
                        this.wireSupport.emit((List<WireRecord>) outputList);
                    } else {
                        for (Object o : outputList) {
                            Map<String, Object> map = JSON.parseObject(JSON.toJSONString(o));
                            this.wireSupport.emit(Utils.toWireRecords(map));
                        }
                    }
                } else if (output instanceof JsonObject) {
                    this.wireSupport.emit(Utils.toWireRecords((JsonObject) output));
                } else if (output instanceof Map) {
                    this.wireSupport.emit(Utils.toWireRecords((Map<String, Object>) output));
                } else {
                    Map<String, Object> map = JSON.parseObject(JSON.toJSONString(output));
                    this.wireSupport.emit(Utils.toWireRecords(map));
                }

            } catch (Exception e) {
                logger.error("Failured to execute script", e);
            }
        } else {
            this.wireSupport.emit(records);
        }
    }

    @SuppressWarnings("unchecked")
    @Activate
    public void activate(final ComponentContext componentContext, MqttClientSubscribeCfg properties)
            throws KuraException {
        logger.info("Activating Script Filter...");
        this.properties = properties;
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
        logger.info("Deactivating Script Filter... Done");
    }

    @Modified
    public synchronized void updated(final MqttClientSubscribeCfg properties) {
        logger.info("Updating Script Filter...");
        serviceTool.ungetServices();
        this.properties = properties;

        scriptSource = properties.script();

        if (!"".equals(scriptSource)) {
            if (webClient != null) {
                webClient.close();
                webClient = WebClient.create(vertx);
            } else {
                webClient = WebClient.create(vertx);
            }
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
        subscribe();
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
        String MqttClientService_target() default "(kura.service.pid=changeme)";

        @AttributeDefinition(name = "%script", cardinality = 0, description = "%scriptDesc", required = false)
        String script() default "";

        @AttributeDefinition(name = "%mqttTopic", cardinality = 0, description = "%mqttTopicDesc", required = true)
        String mqttTopic() default "yofc/iot";

        @AttributeDefinition(name = "%qos", cardinality = 0, description = "%qosDesc", required = true)
        int qos() default 0;

        @AttributeDefinition(name = "%retain", description = "%retainDesc")
        boolean retain() default false;
    }
}
