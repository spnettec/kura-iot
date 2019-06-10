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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.kura.KuraException;
import org.eclipse.kura.asset.AssetService;
import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.crypto.CryptoService;
import org.eclipse.kura.marshalling.Marshaller;
import org.eclipse.kura.message.KuraPayload;
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
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.osgi.service.wireadmin.Consumer;
import org.osgi.service.wireadmin.Wire;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yofc.iot.component.impl.VertxMqttPublisher.VertxMqttPublisherCfg;
import com.yofc.vertx.osgi.api.AssetHelp;
import com.yofc.vertx.osgi.api.Utils;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.mqtt.MqttClientOptions;

@Designate(factory = true, ocd = VertxMqttPublisherCfg.class)
@Component(immediate = true, enabled = true, configurationPolicy = ConfigurationPolicy.REQUIRE, service = {
        ConfigurableComponent.class, WireComponent.class, Consumer.class }, property = {
                "kura.ui.service.hide:Boolean=true", "output.cardinality.minimum:Integer=0",
                "output.cardinality.maximum:Integer=0", "output.cardinality.default:Integer=0",
                "input.cardinality.minimum:Integer=1", "input.cardinality.maximum:Integer=1",
                "input.cardinality.default:Integer=1" })
public class VertxMqttPublisher implements WireReceiver, ConfigurableComponent {

    private static final Logger logger = LoggerFactory.getLogger(VertxMqttPublisher.class);

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
    private ModifyMqttClient mqttClient;
    private VertxMqttPublisherCfg properties;
    @Reference
    private CryptoService cryptoService;
    @Reference(target = "(kura.service.pid=org.eclipse.kura.json.marshaller.unmarshaller.provider)")
    private Marshaller jsonMarshaller;
    @Reference
    private AssetHelp assetHelp;

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
        if (webClient != null) {
            webClient.close();
            webClient = null;
        }
        if (mqttClient != null && mqttClient.isConnected()) {
            mqttClient.disconnect();
            mqttClient = null;
        }
        logger.info("Deactivating Script Filter... Done");
    }

    @Modified
    public synchronized void updated(final VertxMqttPublisherCfg properties) throws KuraException {
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
        if (mqttClient != null && mqttClient.isConnected()) {
            mqttClient.disconnect();
        }
        mqttClient = null;
    }

    @Override
    public synchronized void onWireReceive(WireEnvelope wireEnvelope) {
        if (scriptSource == null) {
            logger.error("Script parse error.Please input correct groovy script");
            return;
        }
        if (mqttClient == null || !mqttClient.isConnected()) {

            logger.error("Mqtt server disconnected!! . Topic{}", this.properties.mqttTopic());
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
                            KuraPayload payload = buildKuraPayload((WireRecord) record);
                            mqttClient.publish(this.properties.mqttTopic(),
                                    Buffer.buffer(jsonMarshaller.marshal(payload)), MqttQoS.AT_MOST_ONCE, false, false);
                            logger.info("Publish MQTT {}", this.properties.mqttTopic());
                        }
                    }
                } else if (output instanceof JsonObject) {
                    KuraPayload payload = buildKuraPayload(Utils.toWireRecord((JsonObject) output));
                    mqttClient.publish(this.properties.mqttTopic(), Buffer.buffer(jsonMarshaller.marshal(payload)),
                            MqttQoS.AT_MOST_ONCE, false, false);
                    logger.info("Publish MQTT {}", this.properties.mqttTopic());
                }

            } catch (Exception e) {
                logger.error("Failured to execute script", e);
            }
        } else {
            for (WireRecord record : wireEnvelope.getRecords()) {
                KuraPayload payload = buildKuraPayload(record);
                try {
                    mqttClient.publish(this.properties.mqttTopic(), Buffer.buffer(jsonMarshaller.marshal(payload)),
                            MqttQoS.AT_MOST_ONCE, false, false);
                } catch (KuraException e) {
                    logger.error("Failed to marshal payload", e);
                }
                logger.info("Publish MQTT {}", this.properties.mqttTopic());
            }
        }
    }

    @Override
    public void updated(Wire wire, Object value) {
        this.wireSupport.updated(wire, value);
    }

    @Override
    public void producersConnected(Wire[] wires) {
        if (wires == null) {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
            }
        } else
            mqttConnect();
        this.wireSupport.producersConnected(wires);
    }

    private void mqttConnect() {
        if (mqttClient != null && mqttClient.isConnected()) {
            mqttClient.disconnect();
        }
        if (mqttClient == null) {
            MqttClientOptions options = new MqttClientOptions();
            options.setCleanSession(true);
            options.setConnectTimeout(10_000);
            options.setAutoGeneratedClientId(true);
            options.setAutoKeepAlive(true);
            options.setKeepAliveTimeSeconds(10);
            if (!properties.username().equals(""))
                options.setUsername(properties.username());
            if (!properties.password().equals(""))
                try {
                    options.setPassword(String.valueOf(cryptoService.decryptAes(properties.password().toCharArray())));
                } catch (KuraException e1) {
                    logger.error("password decrypt failured\n{}", e1);
                    return;
                }
            mqttClient = ModifyMqttClient.create(vertx, options);
        }
        mqttClient.connect(properties.brokerPort(), properties.brokerHost(), properties.brokerHost(), ackMes -> {
            if (ackMes.succeeded()) {
                logger.info("Connect to Server");
            } else {
                logger.error("Failed to connect to a server AckMessage error", ackMes.cause());
                mqttConnect();
            }
        });
        mqttClient.closeHandler(h -> {
            try {
                mqttConnect();
            } catch (Exception e1) {
                logger.error("Failured to execute script", e1);
            }
        });
        mqttClient.exceptionHandler(e -> {
            logger.error("Mqtt error\n", e);
        });
        mqttClient.publishCompletionHandler(h -> {
            logger.info("info:{}", h);
        });
    }

    private KuraPayload buildKuraPayload(final WireRecord wireRecord) {
        requireNonNull(wireRecord, "Wire Record cannot be null");
        final KuraPayload kuraPayload = new KuraPayload();

        kuraPayload.setTimestamp(new Date());

        for (final Entry<String, TypedValue<?>> entry : wireRecord.getProperties().entrySet()) {
            kuraPayload.addMetric(entry.getKey(), entry.getValue().getValue());
        }

        return kuraPayload;
    }

    @ObjectClassDefinition(id = "com.yofc.iot.component.impl.VertxMqttPublisher", name = "%name", description = "%description", localization = "OSGI-INF/l10n/com.yofc.iot.component.impl.VertxMqttPublisherCfg")
    @interface VertxMqttPublisherCfg {

        @AttributeDefinition(name = "%script", cardinality = 0, description = "%scriptDesc", required = false)
        String script()

        default "";

        @AttributeDefinition(name = "%mqttTopic", cardinality = 0, description = "%mqttTopicDesc", required = true)
        String mqttTopic()

        default "yofc/it";

        @AttributeDefinition(name = "%brokerHost", cardinality = 0, description = "%brokerHostDesc", required = true)
        String brokerHost()

        default "localhost";

        @AttributeDefinition(name = "%brokerPort", cardinality = 0, description = "%brokerPortDesc", required = true)
        int brokerPort()

        default 1883;

        @AttributeDefinition(name = "%username", cardinality = 0, description = "%usernameDesc", required = false)
        String username()

        default "mqtt";

        @AttributeDefinition(name = "%password", type = AttributeType.PASSWORD, cardinality = 0, description = "%passwordDesc", required = false)
        String password() default "";
    }

}
