/*******************************************************************************
 * Copyright (c) 2017, 2018 Eurotech and/or its affiliates and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *******************************************************************************/
package com.yofc.iot.component.impl;

import static java.util.Objects.requireNonNull;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.kura.KuraException;
import org.eclipse.kura.asset.AssetService;
import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.crypto.CryptoService;
import org.eclipse.kura.marshalling.Marshaller;
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
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.osgi.service.wireadmin.Consumer;
import org.osgi.service.wireadmin.Producer;
import org.osgi.service.wireadmin.Wire;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson2.JSONObject;
import com.yofc.iot.component.api.ServieTool;
import com.yofc.iot.component.impl.VertxMqttPublisher.VertxMqttPublisherCfg;
import com.yofc.vertx.osgi.api.AssetHelp;
import com.yofc.vertx.osgi.api.Utils;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.WebClient;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.MqttConnectionException;

@Designate(factory = true, ocd = VertxMqttPublisherCfg.class)
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
public class VertxMqttPublisher implements WireEmitter, WireReceiver, ConfigurableComponent {

    private static final Logger logger = LoggerFactory.getLogger(VertxMqttPublisher.class);
    private static final String TOPIC_PATTERN_STRING = "\\$([^\\s/]+)";
    private static final Pattern TOPIC_PATTERN = Pattern.compile(TOPIC_PATTERN_STRING);
    private static final String ASSET_NAME_PROPERTY_KEY = "assetName";
    private final ServieTool serviceTool = new ServieTool();
    private final Map<String, Long> topicTime = new ConcurrentHashMap<>();
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
    private MqttClient mqttClient;
    private VertxMqttPublisherCfg properties;
    @Reference
    private CryptoService cryptoService;
    @Reference(target = "(kura.service.pid=org.eclipse.kura.json.marshaller.unmarshaller.provider)")
    private Marshaller jsonMarshaller;
    @Reference
    private AssetHelp assetHelp;
    private long timerId = 0;

    public void unbindWireHelperService(final WireHelperService wireHelperService) {
        if (this.wireHelperService == wireHelperService) {
            this.wireHelperService = null;
        }
    }

    @SuppressWarnings("unchecked")
    @Activate
    public void activate(final ComponentContext componentContext, VertxMqttPublisherCfg properties)
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
        closeMqttClient();
        logger.info("Deactivating Script Filter... Done");
    }

    @Modified
    public synchronized void updated(final VertxMqttPublisherCfg properties) {
        serviceTool.ungetServices();
        closeMqttClient();
        logger.info("Updating Script Filter...");
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
                logger.error("Script parse error", e);
                scriptSource = null;
            }
            logger.info("Updating Script Filter... Done");
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public synchronized void onWireReceive(Object wireEnvelope) {
        if (scriptSource == null) {
            logger.error("Script parse error.Please input correct groovy script");
            return;
        }
        if (mqttClient == null) {
            mqttConnect();
            logger.warn("Mqtt client is not connected");
            return;
        }
        if (!mqttClient.isConnected()) {
            logger.error("Mqtt client is not connected. Topic: {}", this.properties.mqttTopic());
            return;
        }
        Object output = null;
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
                output = script.getProperty("output");
                if (output == null) {
                    return;
                }

            } catch (Exception e) {
                logger.error("Failured to execute script", e);
            }
        }
        if (output == null) {
            output = wireEnvelope;
        }
        Map<String, Object> result;
        String topic = this.properties.mqttTopic();
        if (output instanceof WireEnvelope) {
            result = Utils.toMap(((WireEnvelope) output).getRecords());

        } else if (List.class.isAssignableFrom(output.getClass()) && !((List<?>) output).isEmpty()
                && ((List<?>) output).get(0) instanceof WireRecord) {
            result = Utils.toMap((List<WireRecord>) output);
        } else {
            result = JSONObject.from(output);
        }
        topic = fillTopicPlaceholders(topic, result);
        if (Boolean.TRUE.equals(isDownGrade(topic))) {
            return;
        }
        topicTime.put(topic, System.currentTimeMillis());
        Map<String, Object> payload = buildKuraPayload(result);
        try {
            mqttClient.publish(topic, Buffer.buffer(JSONObject.toJSONString(payload)),
                    MqttQoS.valueOf(this.properties.qos()), false, false);
        } catch (Exception e) {
            logger.error("Failed to marshal payload", e);
        }

        logger.debug("Publish MQTT {}", topic);

        this.wireSupport.emit(output);
    }

    private Boolean isDownGrade(String topic) {
        long setTime = this.properties.downgradeFre();
        if (setTime == 0) {
            return false;
        }
        Long time = topicTime.get(topic);
        if (time == null) {
            return false;
        }
        Long nowTime = System.currentTimeMillis();
        return (nowTime - time) < setTime;
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
        if (wires == null) {
            closeMqttClient();
        } else {
            mqttConnect();
        }
        this.wireSupport.producersConnected(wires);
    }

    private void closeMqttClient() {
        if (timerId != 0) {
            vertx.cancelTimer(timerId);
            timerId = 0;
        }
        if (mqttClient != null) {
            mqttClient.closeHandler(null);
            mqttClient.exceptionHandler(null);
            mqttClient.publishCompletionHandler(null);
            if (mqttClient.isConnected()) {
                mqttClient.disconnect();
            }
        }
        mqttClient = null;
    }

    private synchronized void reConnect() {
        closeMqttClient();
        timerId = vertx.setTimer(10000, v -> mqttConnect());
    }

    private void mqttConnect() {
        if (mqttClient != null) {
            closeMqttClient();
        }
        if (mqttClient == null) {
            MqttClientOptions options = new MqttClientOptions();
            options.setCleanSession(true);
            options.setConnectTimeout(10_000);
            options.setAutoGeneratedClientId(true);
            options.setAutoKeepAlive(true);
            options.setKeepAliveInterval(10);
            if (!"".equals(properties.username())) {
                options.setUsername(properties.username());
            }
            if (!"".equals(properties.password())) {
                try {
                    options.setPassword(String.valueOf(cryptoService.decryptAes(properties.password().toCharArray())));
                } catch (KuraException e1) {
                    options.setPassword(properties.password());
                }
            }
            mqttClient = MqttClient.create(vertx, options);
        }

        mqttClient.connect(properties.brokerPort(), properties.brokerHost(), properties.brokerHost(), ackMes -> {
            if (ackMes.succeeded()) {
                if (timerId != 0) {
                    vertx.cancelTimer(timerId);
                    timerId = 0;
                }
                logger.info("Connect to Server");
            } else {
                if (ackMes.cause() instanceof MqttConnectionException) {
                    MqttConnectionException exception = (MqttConnectionException) ackMes.cause();
                    if (MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD == exception.code()
                            || MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED == exception.code()) {
                        logger.error("username or password error");
                    }
                } else {
                    try {
                        reConnect();
                    } catch (Exception e1) {
                        logger.error("Failured to reConnect on ackMessage", e1);
                    }
                    logger.error("Failed to connect to a server AckMessage error", ackMes.cause());
                }
            }
        }).closeHandler(h -> {
            try {
                reConnect();
            } catch (Exception e1) {
                logger.error("Failured to reConnect on close", e1);
            }
        }).exceptionHandler(e -> {
            try {
                reConnect();
            } catch (Exception e1) {
                logger.error("Failured to reConnect on exception", e1);
            }
            logger.error("Mqtt error\n", e);
        });
    }

    private Map<String, Object> buildKuraPayload(final Map<String, Object> wireRecords) {
        requireNonNull(wireRecords, "Wire Record cannot be null");
        Map<String, Object> result = new HashMap<>();
        result.put("sentOn", new Date());
        result.put("metrics", wireRecords);
        return result;
    }

    private String fillTopicPlaceholders(String topic, Map<String, Object> wireRecords) {
        Matcher matcher = TOPIC_PATTERN.matcher(topic);
        StringBuilder buffer = new StringBuilder();
        Object value = wireRecords.get(ASSET_NAME_PROPERTY_KEY);
        if (value == null) {
            return topic;
        }
        String value0 = value.toString();
        while (matcher.find()) {
            String replacement = matcher.group(0);
            if (replacement != null) {
                matcher.appendReplacement(buffer, value0);
            }
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    @ObjectClassDefinition(id = "com.yofc.iot.component.impl.VertxMqttPublisher", name = "%name", description = "%description", localization = "OSGI-INF/l10n/com.yofc.iot.component.impl.VertxMqttPublisherCfg")
    @interface VertxMqttPublisherCfg {

        @AttributeDefinition(name = "%script", cardinality = 0, description = "%scriptDesc", required = false)
        String script() default "";

        @AttributeDefinition(name = "%mqttTopic", cardinality = 0, description = "%mqttTopicDesc", required = true)
        String mqttTopic() default "yofc/it";

        @AttributeDefinition(name = "%brokerHost", cardinality = 0, description = "%brokerHostDesc", required = true)
        String brokerHost() default "localhost";

        @AttributeDefinition(name = "%brokerPort", cardinality = 0, description = "%brokerPortDesc", required = true)
        int brokerPort() default 1883;

        @AttributeDefinition(name = "%qos", cardinality = 0, description = "%qosDesc", required = true)
        int qos() default 0;

        @AttributeDefinition(name = "%username", cardinality = 0, description = "%usernameDesc", required = false)
        String username() default "mqtt";

        @AttributeDefinition(name = "%password", type = AttributeType.PASSWORD, cardinality = 0, description = "%passwordDesc", required = false)
        String password() default "";

        @AttributeDefinition(name = "%downgradeFre", cardinality = 0, description = "%downgradeFreDesc", required = true)
        long downgradeFre() default 0;
    }
}
