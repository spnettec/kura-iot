package com.yofc.iot.component.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.osgi.service.metatype.annotations.Option;
import org.osgi.service.wireadmin.Consumer;
import org.osgi.service.wireadmin.Producer;
import org.osgi.service.wireadmin.Wire;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yofc.iot.component.impl.JointValueChangeComponent.JointValueChangeComponentCfg;
import com.yofc.vertx.osgi.api.Utils;

@Designate(factory = true, ocd = JointValueChangeComponentCfg.class)
@Component(immediate = true, enabled = true, configurationPolicy = ConfigurationPolicy.REQUIRE, service = {
        ConfigurableComponent.class, WireComponent.class, WireEmitter.class, WireReceiver.class, Producer.class,
        Consumer.class }, property = { "kura.ui.service.hide:Boolean=true" })
public class JointValueChangeComponent implements WireEmitter, WireReceiver, ConfigurableComponent {

    private static final Logger logger = LoggerFactory.getLogger(JointValueChangeComponent.class);

    @Reference(policy = ReferencePolicy.STATIC, unbind = "unbindWireHelperService")
    private WireHelperService wireHelperService;
    private WireSupport wireSupport;
    private String isOrType = "none";
    private List<String> tagNames = new ArrayList<>();
    private int producersSize = 0;
    private Map<Wire, WireEnvelope> wiresMap = new HashMap<>();

    public void unbindWireHelperService(final WireHelperService wireHelperService) {
        if (this.wireHelperService == wireHelperService) {
            this.wireHelperService = null;
        }
    }

    @SuppressWarnings("unchecked")
    @Activate
    public void activate(final ComponentContext componentContext, final JointValueChangeComponentCfg properties) {
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
    public synchronized void modified(final JointValueChangeComponentCfg properties) {
        updated(properties);
    }

    public synchronized void updated(final JointValueChangeComponentCfg properties) {
        tagNames = new ArrayList<>();

        final String[] v = properties.tags();
        tagNames = Arrays.stream(v).filter(tag -> tag != null && !"".equals(tag)).collect(Collectors.toList());
        isOrType = properties.type();
    }

    @Override
    public synchronized void onWireReceive(WireEnvelope wireEnvelope) {

        this.wireSupport.emit(wireEnvelope.getRecords());
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
        int size = wiresMap.size();
        if (size == this.producersSize - 1) {
            if (wiresMap.containsKey(wire)) {
                wiresMap = new HashMap<>();
                return;
            }
        }
        WireEnvelope wireEnvelope = (WireEnvelope) value;
        wiresMap.put(wire, wireEnvelope);
        if (size < this.producersSize)
            return;
        String pid = wireEnvelope.getEmitterPid();
        List<WireRecord> currentRecords = wiresMap.entrySet().stream().map(entry -> entry.getValue().getRecords())
                .flatMap(Collection::stream).collect(Collectors.toList());
        if ("none".equals(isOrType)) {
            this.wireSupport.updated(wire, new WireEnvelope(pid, covertWireRecodsProperies(currentRecords)));
            wiresMap = new HashMap<>();
        } else {
            List<WireRecord> preRecords = wiresMap.entrySet().stream()
                    .map(entry -> ((WireEnvelope) entry.getKey().getLastValue()).getRecords())
                    .flatMap(Collection::stream).collect(Collectors.toList());

            if (tagNames.isEmpty()) {
                tagNames = Utils.getTagNames(currentRecords);
                tagNames.remove("assetName");
            }
            boolean changed = true;
            if ("or".equals(isOrType)) {
                changed = tagNames.stream().anyMatch(tag -> Utils.isChanged(preRecords, currentRecords, tag));
            } else {
                List<WireRecord> pre = Utils.filterByTages(preRecords, tagNames.toArray(new String[0]));
                List<WireRecord> cur = Utils.filterByTages(currentRecords, tagNames.toArray(new String[0]));
                changed = !pre.equals(cur);
            }
            if (changed) {
                this.wireSupport.updated(wire, new WireEnvelope(pid, covertWireRecodsProperies(currentRecords)));

            }
        }
        wiresMap = new HashMap<>();
    }

    @Override
    public void producersConnected(Wire[] wires) {
        if (wires == null)
            this.producersSize = 0;
        else
            this.producersSize = wires.length;
        this.wireSupport.producersConnected(wires);
    }

    private static List<WireRecord> covertWireRecodsProperies(List<WireRecord> records) {
        List<WireRecord> converted = records.stream()
                .collect(Collectors.mapping(Utils::convertProperies, Collectors.toList()));
        Map<String, TypedValue<?>> properties = converted.stream().map(record -> record.getProperties())
                .map(Map::entrySet).flatMap(Set::stream)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return Utils.getWireRecords(new WireRecord(properties));
    }

    @ObjectClassDefinition(id = "com.yofc.iot.component.impl.JointValueChangeComponent", name = "%name", description = "%description", localization = "OSGI-INF/l10n/com.yofc.iot.component.impl.JointValueChangeComponentCfg")
    @interface JointValueChangeComponentCfg {

        @AttributeDefinition(name = "%type", description = "%typeDesc", options = {
                @Option(label = "none", value = "none"), @Option(label = "and", value = "and"),
                @Option(label = "or", value = "or") }, required = true)
        String type() default "none";

        @AttributeDefinition(name = "%tags", cardinality = -1, description = "%tagsDesc", required = false)
        String[] tags() default "";
    }

}
