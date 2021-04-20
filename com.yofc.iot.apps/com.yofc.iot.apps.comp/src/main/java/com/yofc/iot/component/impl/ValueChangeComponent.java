package com.yofc.iot.component.impl;

import com.yofc.iot.component.impl.ValueChangeComponent.ValueChangeComponentCfg;
import com.yofc.vertx.osgi.api.Utils;
import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.wire.*;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.osgi.service.metatype.annotations.Option;
import org.osgi.service.wireadmin.Consumer;
import org.osgi.service.wireadmin.Producer;
import org.osgi.service.wireadmin.Wire;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Designate(factory = true, ocd = ValueChangeComponentCfg.class)
@Component(
    immediate = true,
    enabled = true,
    configurationPolicy = ConfigurationPolicy.REQUIRE,
    service = {
      ConfigurableComponent.class,
      WireComponent.class,
      WireEmitter.class,
      WireReceiver.class,
      Producer.class,
      Consumer.class
    },
    property = {"kura.ui.service.hide:Boolean=true"})
public class ValueChangeComponent implements WireEmitter, WireReceiver, ConfigurableComponent {

  private static final Logger logger = LoggerFactory.getLogger(ValueChangeComponent.class);

  @Reference(policy = ReferencePolicy.STATIC, unbind = "unbindWireHelperService")
  private WireHelperService wireHelperService;

  private WireSupport wireSupport;
  private boolean isOr = true;
  private List<String> tagNames = new ArrayList<>();

  public void unbindWireHelperService(final WireHelperService wireHelperService) {
    if (this.wireHelperService == wireHelperService) {
      this.wireHelperService = null;
    }
  }

  @SuppressWarnings("unchecked")
  @Activate
  public void activate(
      final ComponentContext componentContext, final ValueChangeComponentCfg properties) {
    logger.info("Activating Script Filter...");
    this.wireSupport =
        this.wireHelperService.newWireSupport(
            this, (ServiceReference<WireComponent>) componentContext.getServiceReference());

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
    String[] v = properties.tags();
    if (v.length == 1) {
      v = v[0].split(",");
    }
    tagNames =
        Arrays.stream(v).filter(tag -> tag != null && !"".equals(tag)).collect(Collectors.toList());
    isOr = "or".equals(properties.type());
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
    WireEnvelope preWireEnvelope = (WireEnvelope) wire.getLastValue();
    if (preWireEnvelope != null) {
      WireEnvelope currentWireEnvelope = (WireEnvelope) value;

      boolean changed = true;
      if (isOr) {
        if (tagNames.isEmpty()) {
          changed = Utils.isOrChanged(preWireEnvelope, currentWireEnvelope);
        } else {
          changed =
              tagNames.stream()
                  .anyMatch(tag -> Utils.isChanged(preWireEnvelope, currentWireEnvelope, tag));
        }
      } else {
        if (tagNames.isEmpty()) {
          changed =
              Utils.isAndChanged(preWireEnvelope.getRecords(), currentWireEnvelope.getRecords());
        } else {
          changed =
              tagNames.stream()
                  .allMatch(tag -> Utils.isChanged(preWireEnvelope, currentWireEnvelope, tag));
        }
      }
      if (changed) {
        this.wireSupport.updated(wire, value);
      }
    }
  }

  @Override
  public void producersConnected(Wire[] wires) {
    this.wireSupport.producersConnected(wires);
  }

  @ObjectClassDefinition(
      id = "com.yofc.iot.component.impl.ValueChangeComponent",
      name = "%name",
      description = "%description",
      localization = "OSGI-INF/l10n/com.yofc.iot.component.impl.ValueChangeComponentCfg")
  @interface ValueChangeComponentCfg {

    @AttributeDefinition(
        name = "%type",
        description = "%typeDesc",
        options = {@Option(label = "and", value = "and"), @Option(label = "or", value = "or")},
        required = true)
    String type() default "or";

    @AttributeDefinition(
        name = "%tags",
        cardinality = -1,
        description = "%tagsDesc",
        required = false)
    String[] tags() default "";
  }
}
