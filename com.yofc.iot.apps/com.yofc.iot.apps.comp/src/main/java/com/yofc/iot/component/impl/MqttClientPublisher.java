package com.yofc.iot.component.impl;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.kura.KuraException;
import org.eclipse.kura.asset.AssetService;
import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.type.TypedValue;
import org.eclipse.kura.wire.WireComponent;
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
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.osgi.service.wireadmin.Consumer;
import org.osgi.service.wireadmin.Wire;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yofc.iot.component.api.MqttClientService;
import com.yofc.iot.component.api.ServieTool;
import com.yofc.iot.component.impl.MqttClientPublisher.MqttClientPublisherCfg;
import com.yofc.vertx.osgi.api.AssetHelp;
import com.yofc.vertx.osgi.api.Utils;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;

@Designate(factory = true, ocd = MqttClientPublisherCfg.class)
@Component(immediate = true, enabled = true, configurationPolicy = ConfigurationPolicy.REQUIRE, service = {
        ConfigurableComponent.class, WireComponent.class, WireReceiver.class,
        Consumer.class }, property = { "kura.ui.service.hide:Boolean=true" })
/*
 * property = {
 * "kura.ui.service.hide:Boolean=true", "output.cardinality.minimum:Integer=0",
 * "output.cardinality.maximum:Integer=0", "output.cardinality.default:Integer=0",
 * "input.cardinality.minimum:Integer=1", "input.cardinality.maximum:Integer=1",
 * "input.cardinality.default:Integer=1" }
 *
 */
public class MqttClientPublisher implements WireReceiver, ConfigurableComponent {

    private static final Logger logger = LoggerFactory.getLogger(MqttClientPublisher.class);
    private static final String TOPIC_PATTERN_STRING = "\\$([^\\s/]+)";
    private static final Pattern TOPIC_PATTERN = Pattern.compile(TOPIC_PATTERN_STRING);
    private static final String ASSET_NAME_PROPERTY_KEY = "assetName";
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

    private MqttClientPublisherCfg properties;

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
        serviceTool.ungetServices();
        if (webClient != null) {
            webClient.close();
            webClient = null;
        }
        logger.info("Deactivating Script Filter... Done");
    }

    @Modified
    public synchronized void updated(final MqttClientPublisherCfg properties) throws KuraException {
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
            }
            logger.info("Updating Script Filter... Done");
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onWireReceive(Object wireEnvelope) {
        if (scriptSource == null) {
            logger.error("Script parse error.Please input correct groovy script");
            return;
        }
        if (this.mqttClientService == null) {
            logger.error("mqttClientService not attched");
            return;
        }
        String topic = this.properties.mqttTopic();
        if (wireEnvelope instanceof WireEnvelope) {
            WireRecord wireRecord = ((WireEnvelope) wireEnvelope).getRecords().get(0);
            topic = fillTopicPlaceholders(topic, wireRecord);
        }
        if (!"".equals(this.scriptSource)) {
            try {
                script.setProperty("input", wireEnvelope);
                Object outputEnvelopeWrapper = wireEnvelope;
                if (wireEnvelope instanceof WireEnvelope) {
                    outputEnvelopeWrapper = Utils.toMap(((WireEnvelope) wireEnvelope).getRecords());
                } else if (List.class.isAssignableFrom(wireEnvelope.getClass()) && !((List<?>) wireEnvelope).isEmpty()
                        && ((List<?>) wireEnvelope).get(0) instanceof WireRecord) {
                    outputEnvelopeWrapper = Utils.toMap((List<WireRecord>) wireEnvelope);
                }
                script.setProperty("output", outputEnvelopeWrapper);
                script.run();
                Object output = script.getProperty("output");
                if (output == null) {
                    return;
                }

                this.mqttClientService.publish(topic, this.properties.qos(), this.properties.retain(), output);
                logger.debug("Publish topic: {}, data: {}", topic, output);
            } catch (Exception e) {
                logger.error("Failured to execute script", e);
            }
        } else {
            try {
                Object output = wireEnvelope;
                if (wireEnvelope instanceof WireEnvelope) {
                    output = Utils.toMap(((WireEnvelope) wireEnvelope).getRecords());
                }
                this.mqttClientService.publish(topic, this.properties.qos(), this.properties.retain(), output);
                logger.debug("Publish topic: {}, data: {}", topic, output);
            } catch (KuraException e) {
                logger.error("Failed to marshal payload", e);
            }
        }
    }

    private String fillTopicPlaceholders(String topic, WireRecord wireRecord) {
        Matcher matcher = TOPIC_PATTERN.matcher(topic);
        StringBuilder buffer = new StringBuilder();
        Map<String, TypedValue<?>> wireRecordProperties = wireRecord.getProperties();
        TypedValue<?> value = wireRecordProperties.get(ASSET_NAME_PROPERTY_KEY);
        if (value == null) {
            return topic;
        }
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
