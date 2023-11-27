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

import org.eclipse.kura.KuraException;
import org.eclipse.kura.asset.AssetService;
import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.crypto.CryptoService;
import org.eclipse.kura.marshalling.Marshaller;
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
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.osgi.service.wireadmin.Producer;
import org.osgi.service.wireadmin.Wire;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yofc.iot.component.api.ServieTool;
import com.yofc.iot.component.impl.VertxMqttPublisher.VertxMqttPublisherCfg;
import com.yofc.vertx.osgi.api.AssetHelp;
import com.yofc.vertx.osgi.api.Utils;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.MqttConnectionException;
import io.vertx.mqtt.messages.MqttPublishMessage;

@Designate(factory = true, ocd = VertxMqttSubscribe.VertxMqttSubscribeCfg.class)
@Component(immediate = true, enabled = true, configurationPolicy = ConfigurationPolicy.REQUIRE, service = {
        WireComponent.class, WireEmitter.class, ConfigurableComponent.class,
        Producer.class }, property = { "kura.ui.service.hide:Boolean=true" })
public class VertxMqttSubscribe implements WireEmitter, ConfigurableComponent {

    private static final Logger logger = LoggerFactory.getLogger(VertxMqttSubscribe.class);
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
    public synchronized void updated(final VertxMqttPublisherCfg properties) throws KuraException {
        serviceTool.ungetServices();
        closeMqttClient();
        logger.info("Updating Script Filter...");
        this.properties = properties;

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
                logger.error("Script parse error", e);
                scriptSource = null;
            }
            logger.info("Updating Script Filter... Done");
        }
        mqttConnect();
    }

    @Override
    public Object polled(Wire wire) {
        return this.wireSupport.polled(wire);
    }

    @Override
    public void consumersConnected(Wire[] wires) {
        this.wireSupport.consumersConnected(wires);
    }

    private void closeMqttClient() {
        if (timerId != 0) {
            vertx.cancelTimer(timerId);
            timerId = 0;
        }
        if (mqttClient != null) {
            mqttClient.closeHandler(null);
            mqttClient.exceptionHandler(null);
            mqttClient.unsubscribe(this.properties.mqttTopic());
            mqttClient.publishCompletionHandler(null);
            if (mqttClient.isConnected()) {
                mqttClient.disconnect();
            }
        }
        mqttClient = null;
    }

    private synchronized void reConnect() {
        closeMqttClient();
        timerId = vertx.setTimer(10000, v -> {
            mqttConnect();
        });
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
                mqttClient.subscribe(this.properties.mqttTopic(), this.properties.qos());
                mqttClient.subscribeCompletionHandler(mqttSubAckMessage -> {
                    logger.debug("Id of just received SUBACK packet is {}", mqttSubAckMessage.messageId());
                    for (int s : mqttSubAckMessage.grantedQoSLevels()) {
                        if (s == 0x80) {
                            logger.error("Failure");
                        } else {
                            mqttClient.publishHandler(this::handleMqttMessage);
                        }
                    }
                });
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

    private void handleMqttMessage(MqttPublishMessage msg) {
        WireRecord wireRecord = Utils.bufferToWireRecord(msg.topicName(), msg.payload());
        Object output = null;
        if (!"".equals(this.scriptSource)) {
            try {

                script.setProperty("input", wireRecord);
                WireRecord outputEnvelopeWrapper = Utils.bufferToWireRecord(msg.topicName(), msg.payload());
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

    @ObjectClassDefinition(id = "com.yofc.iot.component.impl.VertxMqttSubscribe", name = "%name", description = "%description", localization = "OSGI-INF/l10n/com.yofc.iot.component.impl.VertxMqttSubscribeCfg")
    @interface VertxMqttSubscribeCfg {

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

    }
}
