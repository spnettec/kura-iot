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
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.kura.configuration.ConfigurableComponent;
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
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.osgi.service.wireadmin.Consumer;
import org.osgi.service.wireadmin.Producer;
import org.osgi.service.wireadmin.Wire;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yofc.iot.component.api.ServieTool;
import com.yofc.iot.component.impl.GroovyScriptFilter.GroovyScriptFilterCfg;
import com.yofc.vertx.osgi.api.AssetHelp;
import com.yofc.vertx.osgi.api.Utils;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

@Designate(factory = true, ocd = GroovyScriptFilterCfg.class)
@Component(immediate = true, enabled = true, configurationPolicy = ConfigurationPolicy.REQUIRE, service = {
        ConfigurableComponent.class, WireComponent.class, WireEmitter.class, WireReceiver.class, Producer.class,
        Consumer.class }, property = { "kura.ui.service.hide:Boolean=true" })
public class GroovyScriptFilter implements WireEmitter, WireReceiver, ConfigurableComponent {

    private static final Logger logger = LoggerFactory.getLogger(GroovyScriptFilter.class);
    private final ServieTool serviceTool = new ServieTool();
    private String scriptSource = "";
    @Reference(unbind = "unbindWireHelperService")
    private WireHelperService wireHelperService;
    @Reference
    private Vertx vertx;
    private WebClient webClient;
    private WireSupport wireSupport;
    private Script script;
    @Reference
    private AssetHelp assetHelp;

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
    public synchronized void updated(final GroovyScriptFilterCfg properties) {
        logger.info("Updating Script Filter...");
        serviceTool.ungetServices();
        scriptSource = properties.script();

        if (!"".equals(scriptSource) || !"".equals(properties.initScript())) {
            Utils.executeWithTCCLSwitch(() -> {
                if (webClient != null) {
                    webClient.close();
                }
                webClient = WebClient.create(vertx, new WebClientOptions().setIdleTimeout(60000));
            });

            Binding binding = new Binding();
            GroovyShell shell = new GroovyShell(binding);
            binding.setVariable("logger", logger);
            binding.setVariable("webClient", webClient);
            binding.setVariable("assetHelp", assetHelp);
            binding.setVariable("serviceTool", serviceTool);
            binding.setVariable("vertx", vertx);
            if (!"".equals(properties.initScript())) {
                if (script != null && !script.getMetaClass().respondsTo(script, "destroy").isEmpty()) {
                    try {
                        script.invokeMethod("destroy", null);
                    } catch (Exception e) {
                        logger.error("Script destroy method error", e);
                    }
                }
                scriptSource = scriptSource + "\r\n" + properties.initScript();
            }
            try {
                script = shell.parse(scriptSource);
            } catch (Exception e) {
                logger.error("Script parse error", e);
                scriptSource = null;
                return;
            }
            if (!"".equals(properties.initScript()) && !script.getMetaClass().respondsTo(script, "init").isEmpty()) {
                try {
                    script.invokeMethod("init", null);
                } catch (Exception e) {
                    logger.error("Script init method error", e);
                }
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
        Object output = null;
        if (!"".equals(scriptSource)) {

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
                if (output != null && !(output instanceof CompletableFuture || output instanceof Promise
                        || output instanceof Future)) {
                    wireSupport.emit(output);
                    return;
                }
                if (output != null) {
                    if (output instanceof CompletableFuture) {
                        CompletableFuture<Object> future = (CompletableFuture<Object>) output;
                        future.whenComplete((obj, e) -> {
                            if (e != null) {
                                wireSupport.emit(e.toString());
                            } else {
                                if (obj == null) {
                                    wireSupport.emit("");
                                } else {
                                    wireSupport.emit(obj.toString());
                                }
                            }
                        });
                    } else if (output instanceof Promise) {
                        Promise<Object> promise = (Promise<Object>) output;
                        promise.future().onComplete(v -> {
                            if (v.failed()) {
                                wireSupport.emit(v.cause() == null ? "unknow reson" : v.cause().getMessage());
                            } else {
                                wireSupport.emit(v.result().toString());
                            }
                        });

                    } else if (output instanceof Future) {
                        Future<Object> future = (Future<Object>) output;
                        future.onComplete(v -> {
                            if (v.failed()) {
                                wireSupport.emit(v.cause().getMessage());
                            } else {
                                wireSupport.emit(v.result().toString());
                            }
                        });

                    } else {
                        wireSupport.emit(output);
                    }
                }
            } catch (Throwable e) {
                wireSupport.emit(e.toString());
            }
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

    @ObjectClassDefinition(id = "com.yofc.iot.component.impl.GroovyScriptFilter", name = "%name", description = "%description", localization = "OSGI-INF/l10n/com.yofc.iot.component.impl.GroovyScriptFilterCfg")
    @interface GroovyScriptFilterCfg {

        @AttributeDefinition(name = "%script", cardinality = 0, description = "%scriptDesc", required = false)
        String script() default "";

        @AttributeDefinition(name = "%initScript", cardinality = 0, description = "%initScriptDesc", required = false)
        String initScript() default "";
    }
}
