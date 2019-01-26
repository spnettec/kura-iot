package com.yofc.iot.component.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.type.TypedValue;
import org.eclipse.kura.wire.WireComponent;
import org.eclipse.kura.wire.WireEmitter;
import org.eclipse.kura.wire.WireEnvelope;
import org.eclipse.kura.wire.WireHelperService;
import org.eclipse.kura.wire.WireReceiver;
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
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.wireadmin.Consumer;
import org.osgi.service.wireadmin.Producer;
import org.osgi.service.wireadmin.Wire;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yofc.vertx.osgi.api.Utils;

@Designate(factory = true, ocd = ValueChangeComponentCfg.class)
@Component(immediate = true, enabled = true, configurationPolicy = ConfigurationPolicy.REQUIRE, service = {
        ConfigurableComponent.class, WireComponent.class, WireEmitter.class, WireReceiver.class, Producer.class,
        Consumer.class }, property = { "kura.ui.service.hide:Boolean=true" })
public class ValueChangeComponent implements WireEmitter, WireReceiver, ConfigurableComponent {

    private static final Logger logger = LoggerFactory.getLogger(ValueChangeComponent.class);

    @Reference(policy = ReferencePolicy.STATIC, unbind = "unbindWireHelperService")
    private volatile WireHelperService wireHelperService;
    private WireSupport wireSupport;
    private WireEnvelope preWireEnvelope;
    private boolean isOr = true;
    private List<String> tagNames = new ArrayList<>();
    private List<String> changedTagNames = new ArrayList<>();

    public void unbindWireHelperService(final WireHelperService wireHelperService) {
        if (this.wireHelperService == wireHelperService) {
            this.wireHelperService = null;
        }
    }

    @SuppressWarnings("unchecked")
    @Activate
    public void activate(final ComponentContext componentContext, final ValueChangeComponentCfg properties) {
        logger.info("Activating Script Filter...");
        this.wireSupport = this.wireHelperService.newWireSupport(this,
                (ServiceReference<WireComponent>) componentContext.getServiceReference());

        updated(properties);

        logger.info("Activating ValueChanged Component... Done");
    }

    @Deactivate
    public void deactivate() {
        logger.info("Deactivating ValueChanged Component...");
    }

    @Modified
    public synchronized void modified(final ValueChangeComponentCfg properties) {
        updated(properties);
    }

    public synchronized void updated(final ValueChangeComponentCfg properties) {
        tagNames = new ArrayList<>();

        final String[] v = properties.tags();
        tagNames.addAll(Arrays.asList((String[]) v));
        isOr = "or".equals(properties.type());
        changedTagNames = new ArrayList<>();
    }

    @Override
    public synchronized void onWireReceive(WireEnvelope wireEnvelope) {
        if (tagNames.isEmpty()) {
            tagNames = Utils.getTagNames(wireEnvelope.getRecords());
            tagNames.remove("assetName");
        }
        WireEnvelope currentWireEnvelope = wireEnvelope;
        if (preWireEnvelope == null || preWireEnvelope == currentWireEnvelope) {
            preWireEnvelope = currentWireEnvelope;
            return;
        }
        boolean changed = true;
        if (isOr) {
            changed = tagNames.stream().anyMatch(tag -> {
                return isChanged(preWireEnvelope, currentWireEnvelope, tag);
            });

        } else {
            List<String> testTags = tagNames.stream().filter(t -> {
                return !changedTagNames.contains(t);
            }).collect(Collectors.toList());
            testTags.stream().forEach(tag -> {
                boolean isChanged = isChanged(preWireEnvelope, currentWireEnvelope, tag);
                if (isChanged)
                    changedTagNames.add(tag);
            });
            if (changedTagNames.size() >= tagNames.size()) {
                changed = true;
                changedTagNames = new ArrayList<>();
            } else
                changed = false;

        }
        preWireEnvelope = currentWireEnvelope;
        if (changed)
            this.wireSupport.emit(currentWireEnvelope.getRecords());
    }

    private boolean isChanged(WireEnvelope preWireEnvelope, WireEnvelope currentWireEnvelope, String tag) {
        TypedValue<?> prValue = Utils.getValue(tag, preWireEnvelope.getRecords());
        TypedValue<?> cuValue = Utils.getValue(tag, currentWireEnvelope.getRecords());
        boolean isSame = prValue.getValue().equals(cuValue.getValue());
        return !isSame;

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
