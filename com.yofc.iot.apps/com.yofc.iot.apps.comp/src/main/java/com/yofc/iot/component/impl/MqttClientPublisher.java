package com.yofc.iot.component.impl;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.kura.KuraException;
import org.eclipse.kura.asset.AssetService;
import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.message.KuraPayload;
import org.eclipse.kura.type.TypedValue;
import org.eclipse.kura.wire.WireComponent;
import org.eclipse.kura.wire.WireEmitter;
import org.eclipse.kura.wire.WireEnvelope;
import org.eclipse.kura.wire.WireHelperService;
import org.eclipse.kura.wire.WireReceiver;
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
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.osgi.service.wireadmin.Consumer;
import org.osgi.service.wireadmin.Producer;
import org.osgi.service.wireadmin.Wire;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yofc.iot.component.api.MqttClientService;
import com.yofc.iot.component.impl.MqttClientPublisher.MqttClientPublisherCfg;
import com.yofc.vertx.osgi.api.AssetHelp;
import com.yofc.vertx.osgi.api.Utils;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;

@Designate(factory = true, ocd = MqttClientPublisherCfg.class)
@Component(immediate = true, enabled = true, configurationPolicy = ConfigurationPolicy.REQUIRE, service = {
        ConfigurableComponent.class, WireComponent.class, WireEmitter.class, WireReceiver.class, Producer.class,
        Consumer.class }, property = { "kura.ui.service.hide:Boolean=true" })
/*
 * property = {
 * "kura.ui.service.hide:Boolean=true", "output.cardinality.minimum:Integer=0",
 * "output.cardinality.maximum:Integer=0", "output.cardinality.default:Integer=0",
 * "input.cardinality.minimum:Integer=1", "input.cardinality.maximum:Integer=1",
 * "input.cardinality.default:Integer=1" }
 * 
 */
public class MqttClientPublisher implements WireEmitter, WireReceiver, ConfigurableComponent {

    private static final Logger logger = LoggerFactory.getLogger(MqttClientPublisher.class);
    private static final String TOPIC_PATTERN_STRING = "\\$([^\\s/]+)";
    private static final Pattern TOPIC_PATTERN = Pattern.compile(TOPIC_PATTERN_STRING);
    private static final String ASSET_NAME_PROPERTY_KEY = "assetName";
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
    @Reference(name = "MqttClientService", policy = ReferencePolicy.DYNAMIC, unbind = "unbindMqttClientService")
    private volatile MqttClientService mqttClientService;
    private MqttClientPublisherCfg properties;

    public void unbindWireHelperService(final WireHelperService wireHelperService) {
        if (this.wireHelperService == wireHelperService) {
            this.wireHelperService = null;
        }
    }

    public void unbindMqttClientService(MqttClientService mqttClientService) {
        if (this.mqttClientService == mqttClientService)
            this.mqttClientService = null;
    }

    @SuppressWarnings("unchecked")
    @Activate
    public void activate(final ComponentContext componentContext, MqttClientPublisherCfg properties)
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
        if (webClient != null) {
            webClient.close();
            webClient = null;
        }
        logger.info("Deactivating Script Filter... Done");
    }

    @Modified
    public synchronized void updated(final MqttClientPublisherCfg properties) throws KuraException {
        logger.info("Updating Script Filter...");
        this.properties = properties;

        scriptSource = properties.script();

        if (!scriptSource.equals("")) {
            if (webClient != null) {
                webClient.close();
                webClient = WebClient.create(vertx);
            } else
                webClient = WebClient.create(vertx);
            Binding binding = new Binding();
            GroovyShell shell = new GroovyShell(binding);
            binding.setVariable("logger", logger);
            binding.setVariable("webClient", webClient);
            binding.setVariable("assetHelp", assetHelp);
            binding.setVariable("vertx", vertx);
            try {
                script = shell.parse(scriptSource);
            } catch (Exception e) {
                logger.error("Script parse error:{}", e);
                scriptSource = null;
            }
            logger.info("Updating Script Filter... Done");
        }
    }

    @Override
    public void onWireReceive(WireEnvelope wireEnvelope) {
        if (scriptSource == null) {
            logger.error("Script parse error.Please input correct groovy script");
            return;
        }
        if (this.mqttClientService == null) {
            logger.error("mqttClientService not attched");
            return;
        }

        if (!this.scriptSource.equals("")) {
            try {
                script.setProperty("input", wireEnvelope);
                List<WireRecord> outputEnvelopeWrapper = new ArrayList<>();
                for (WireRecord record : wireEnvelope.getRecords()) {
                    Map<String, TypedValue<?>> mapCopy = record.getProperties();
                    outputEnvelopeWrapper.add(new WireRecord(mapCopy));
                }
                script.setProperty("output", outputEnvelopeWrapper);
                script.run();
                Object output = script.getProperty("output");
                if (output == null)
                    return;
                if (output instanceof List) {
                    List<?> outputList = (List<?>) output;
                    if (outputList.isEmpty())
                        return;
                    if (outputList.get(0) instanceof WireRecord) {
                        for (Object record : outputList) {
                            WireRecord wireRecord = (WireRecord) record;
                            String topic = fillTopicPlaceholders(this.properties.mqttTopic(), wireRecord);
                            this.mqttClientService.publish(topic, this.properties.qos(), this.properties.retain(),
                                    buildKuraPayload(wireRecord).metrics());
                            this.wireSupport.emit(wireEnvelope.getRecords());
                            logger.info("Publish MQTT {}", topic);
                        }
                    }
                } else if (output instanceof JsonObject) {
                    WireRecord wireRecord = Utils.toWireRecord((JsonObject) output);
                    String topic = fillTopicPlaceholders(this.properties.mqttTopic(), wireRecord);
                    this.mqttClientService.publish(topic, this.properties.qos(), this.properties.retain(),
                            buildKuraPayload(wireRecord).metrics());
                    this.wireSupport.emit(wireEnvelope.getRecords());
                    logger.info("Publish MQTT {}", topic);
                }

            } catch (Exception e) {
                logger.error("Failured to execute script", e);
            }
        } else {
            for (WireRecord record : wireEnvelope.getRecords()) {
                String topic = fillTopicPlaceholders(this.properties.mqttTopic(), record);

                try {
                    this.mqttClientService.publish(topic, this.properties.qos(), this.properties.retain(),
                            buildKuraPayload(record).metrics());
                } catch (KuraException e) {
                    logger.error("Failed to marshal payload", e);
                }
                this.wireSupport.emit(wireEnvelope.getRecords());
                logger.info("Publish MQTT {}", topic);
            }
        }
    }

    private KuraPayload buildKuraPayload(final WireRecord wireRecord) {
        requireNonNull(wireRecord, "Wire Record cannot be null");
        final KuraPayload kuraPayload = new KuraPayload();
        for (final Entry<String, TypedValue<?>> entry : wireRecord.getProperties().entrySet()) {
            kuraPayload.addMetric(entry.getKey(), entry.getValue().getValue());
        }
        return kuraPayload;
    }

    private String fillTopicPlaceholders(String topic, WireRecord wireRecord) {
        Matcher matcher = TOPIC_PATTERN.matcher(topic);
        StringBuffer buffer = new StringBuffer();
        Map<String, TypedValue<?>> properties = wireRecord.getProperties();
        TypedValue<?> value = properties.get(ASSET_NAME_PROPERTY_KEY);
        if (value == null)
            return topic;
        String value0 = value.getValue().toString();
        while (matcher.find()) {
            String replacement = matcher.group(0);
            if (replacement != null) {
                matcher.appendReplacement(buffer, value0);
            }
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    @Override
    public Object polled(Wire wire) {
        return this.wireSupport.polled(wire);
    }

    @Override
    public void consumersConnected(Wire[] wires) {
        this.wireSupport.consumersConnected(wires);
    }

    @Override
    public void updated(Wire wire, Object value) {
        this.wireSupport.updated(wire, value);
    }

    @Override
    public void producersConnected(Wire[] wires) {
        this.wireSupport.producersConnected(wires);
    }

    @ObjectClassDefinition(id = "com.yofc.iot.component.impl.MqttClientPublisher", name = "%name", description = "%description", localization = "OSGI-INF/l10n/com.yofc.iot.component.impl.MqttClientPublisherCfg")
    @interface MqttClientPublisherCfg {

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
