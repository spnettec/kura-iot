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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.eclipse.kura.configuration.ConfigurableComponent;
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
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.wireadmin.Consumer;
import org.osgi.service.wireadmin.Producer;
import org.osgi.service.wireadmin.Wire;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;

@Designate(factory = true, ocd = GroovyScriptFilterCfg.class)
@Component(immediate = true, enabled = true, configurationPolicy = ConfigurationPolicy.REQUIRE, service = {
        ConfigurableComponent.class, WireComponent.class, WireEmitter.class, WireReceiver.class, Producer.class,
        Consumer.class }, property = { "kura.ui.service.hide:Boolean=true" })
public class GroovyScriptFilter implements WireEmitter, WireReceiver, ConfigurableComponent {

    private static final Logger logger = LoggerFactory.getLogger(GroovyScriptFilter.class);

    private String scriptSource;
    @Reference(bind = "bindWireHelperService", unbind = "unbindWireHelperService")
    private volatile WireHelperService wireHelperService;
    @Reference
    private Vertx vertx;
    private WebClient webClient;
    private WireSupport wireSupport;

    public void bindWireHelperService(final WireHelperService wireHelperService) {
        if (this.wireHelperService == null) {
            this.wireHelperService = wireHelperService;
        }
    }

    public void unbindWireHelperService(final WireHelperService wireHelperService) {
        if (this.wireHelperService == wireHelperService) {
            this.wireHelperService = null;
        }
    }

    @SuppressWarnings("unchecked")
    @Activate
    public void activate(final ComponentContext componentContext, GroovyScriptFilterCfg properties) throws IOException {
        logger.info("Activating Script Filter...");
        this.wireSupport = this.wireHelperService.newWireSupport(this,
                (ServiceReference<WireComponent>) componentContext.getServiceReference());
        webClient = WebClient.create(vertx);
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
    public synchronized void updated(final GroovyScriptFilterCfg properties) {
        logger.info("Updating Script Filter...");

        scriptSource = properties.script();

        if (scriptSource == null) {
            logger.warn("Script source is null");
            return;
        }
        if (webClient != null) {
            webClient.close();
            webClient = WebClient.create(vertx);
        }
        logger.info("Updating Script Filter... Done");
    }

    @Override
    public synchronized void onWireReceive(WireEnvelope wireEnvelope) {

        if (this.scriptSource == null) {
            logger.warn("Failed to compile script");
            return;
        }

        try {
            Binding binding = new Binding();
            GroovyShell shell = new GroovyShell(binding);
            binding.setVariable("logger", logger);
            binding.setVariable("input", wireEnvelope);
            binding.setVariable("webClient", webClient);
            List<WireRecord> outputEnvelopeWrapper = new ArrayList<>();
            for (WireRecord record : wireEnvelope.getRecords()) {
                Map<String, TypedValue<?>> mapCopy = record.getProperties().entrySet().stream()
                        .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
                outputEnvelopeWrapper.add(new WireRecord(mapCopy));
            }
            binding.setVariable("output", outputEnvelopeWrapper);
            shell.evaluate(this.scriptSource);
            if (outputEnvelopeWrapper.isEmpty())
                return;

            this.wireSupport.emit(outputEnvelopeWrapper);

        } catch (Exception e) {
            logger.warn("Failed to execute script", e);
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

    @Override
    public void updated(Wire wire, Object value) {
        this.wireSupport.updated(wire, value);
    }

    @Override
    public void producersConnected(Wire[] wires) {
        this.wireSupport.producersConnected(wires);
    }
}
