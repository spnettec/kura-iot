package com.yofc.iot.apps.influxdbv1;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.crypto.CryptoService;
import org.eclipse.kura.wire.WireComponent;
import org.eclipse.kura.wire.WireEnvelope;
import org.eclipse.kura.wire.WireHelperService;
import org.eclipse.kura.wire.WireReceiver;
import org.eclipse.kura.wire.WireRecord;
import org.eclipse.kura.wire.WireSupport;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Point;
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

import com.alibaba.fastjson.JSON;
import com.yofc.iot.apps.influxdbv1.InfluxdbComponent.Config;
import com.yofc.vertx.osgi.api.AssetHelp;
import com.yofc.vertx.osgi.api.Utils;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;

@Designate(factory = true, ocd = Config.class)
@Component(immediate = true, enabled = true, configurationPolicy = ConfigurationPolicy.REQUIRE, service = {
        ConfigurableComponent.class, WireComponent.class, WireReceiver.class,
        Consumer.class }, property = { "kura.ui.service.hide:Boolean=true" })
/*
 * /*
 * property = {
 * "kura.ui.service.hide:Boolean=true", "output.cardinality.minimum:Integer=0",
 * "output.cardinality.maximum:Integer=0", "output.cardinality.default:Integer=0",
 * "input.cardinality.minimum:Integer=1", "input.cardinality.maximum:Integer=1",
 * "input.cardinality.default:Integer=1" }
 *
 */
public class InfluxdbComponent implements WireReceiver, ConfigurableComponent {

    private static final Logger logger = LoggerFactory.getLogger(InfluxdbComponent.class);

    private InfluxDB client;
    private String scriptSource = "";
    @Reference
    private CryptoService cryptoService;
    @Reference(unbind = "unbindWireHelperService")
    private WireHelperService wireHelperService;
    @Reference
    private Vertx vertx;
    private WebClient webClient;
    private WireSupport wireSupport;
    private Script script;
    private Config config;
    @Reference
    private AssetHelp assetHelp;

    public void unbindWireHelperService(final WireHelperService wireHelperService) {
        if (this.wireHelperService == wireHelperService) {
            this.wireHelperService = null;
        }
    }

    @SuppressWarnings("unchecked")
    @Activate
    public void activate(final ComponentContext componentContext, Config config) throws IOException {
        logger.info("Activating Script Filter...");
        this.wireSupport = this.wireHelperService.newWireSupport(this,
                (ServiceReference<WireComponent>) componentContext.getServiceReference());

        updated(config);

        logger.info("ActivatingScript Filter... Done");
    }

    @Deactivate
    public void deactivate() {
        logger.info("Deactivating Script Filter...");
        if (webClient != null) {
            webClient.close();
            webClient = null;
        }
        if (this.client != null) {
            this.client.close();
            this.client = null;
        }
        logger.info("Deactivating Script Filter... Done");
    }

    @Modified
    public synchronized void updated(final Config config) {
        logger.info("Updating Script Filter...");
        scriptSource = config.script();
        this.config = config;
        if (this.client != null) {
            this.client.close();
            this.client = null;
        }
        String password = config.password();
        if (password == null) {
            password = "";
        }
        try {
            char[] pass = this.cryptoService.decryptAes(password.toCharArray());
            password = new String(pass);
        } catch (Exception e) {
            logger.info("Password is not encrypted");
        }
        this.client = InfluxDBFactory.connect(config.url(), config.username(), password);
        this.client.setDatabase(config.database());
        this.client.setRetentionPolicy(config.retentionPolicy());

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
            binding.setVariable("vertx", vertx);
            binding.setVariable("dbClient", client);
            if (!"".equals(config.initScript())) {
                scriptSource = scriptSource + "\r\ndef initComponent(){\r\n" + config.initScript() + "\r\n}";
            }
            try {
                script = shell.parse(scriptSource);
                if (!"".equals(config.initScript())) {
                    script.invokeMethod("initComponent", null);
                }
            } catch (Exception e) {
                logger.error("Script parse error", e);
                scriptSource = null;
            }
            logger.info("Updating Script Filter... Done");
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public synchronized void onWireReceive(WireEnvelope wireEnvelope) {
        if (scriptSource == null) {
            logger.error("Script parse error.Please input correct groovy script");
            return;
        }
        if (!"".equals(scriptSource)) {

            try {
                script.setProperty("input", wireEnvelope);
                Map<String, Object> outputEnvelopeWrapper = Utils.toMap(wireEnvelope.getRecords());
                script.setProperty("output", outputEnvelopeWrapper);
                script.run();
                Object output = script.getProperty("output");
                if (output == null) {
                    return;
                }
                if (output instanceof List) {
                    List<?> outputList = (List<?>) output;
                    if (outputList.isEmpty()) {
                        return;
                    }
                    if (outputList.get(0) instanceof WireRecord) {
                        this.wireSupport.emit((List<WireRecord>) outputList);
                    } else {
                        for (Object o : outputList) {
                            Map<String, Object> map = JSON.parseObject(JSON.toJSONString(o));
                            saveToInfluxdb(Utils.toWireRecords(map));
                        }
                    }
                } else if (output instanceof JsonObject) {
                    saveToInfluxdb(Utils.toWireRecords((JsonObject) output));
                } else if (output instanceof Map) {
                    saveToInfluxdb(Utils.toWireRecords((Map<String, Object>) output));
                } else {
                    Map<String, Object> map = JSON.parseObject(JSON.toJSONString(output));
                    saveToInfluxdb(Utils.toWireRecords(map));
                }
            } catch (Exception e) {
                logger.warn("Failed to execute script", e);
            }
        } else {
            saveToInfluxdb(wireEnvelope.getRecords());
        }
    }

    private void saveToInfluxdb(List<WireRecord> wires) {
        Point.Builder pointBuilder = Point.measurement(this.config.measurement());
        pointBuilder.tag(parseTags(this.config.tags()));
        Map<String, Object> result = Utils.toMap(wires);
        result.remove("assetName");
        result.entrySet().stream().forEach(entry -> {
            pointBuilder.field(entry.getKey(), entry.getValue());
        });
        this.client.write(pointBuilder.build());

    }

    private static Map<String, String> parseTags(final String string) {
        Map<String, String> map = new HashMap<>();
        if (string == null || string.isEmpty()) {
            return map;
        }
        final String[] reqs = string.split("\\s*,\\s*");
        for (String req : reqs) {
            final String[] toks = req.split("\\=", 2);
            if (toks.length == 2) {
                final String tagName = toks[0];
                final String tagValue = toks[1];
                map.put(tagName, tagValue);
            }
        }
        return map;
    }

    @Override
    public void updated(Wire wire, Object value) {
        this.wireSupport.updated(wire, value);
    }

    @Override
    public void producersConnected(Wire[] wires) {
        this.wireSupport.producersConnected(wires);
    }

    @ObjectClassDefinition(id = "com.yofc.iot.apps.influxdbv1.InfluxdbComponent", name = "%name", description = "%description", localization = "OSGI-INF/l10n/com.yofc.iot.apps.influxdbv1.InfluxdbComponentCfg")
    @interface Config {

        @AttributeDefinition(name = "%url", description = "%urlDesc")
        String url();

        @AttributeDefinition(name = "%measurement", description = "%measurementDesc", required = false)
        String measurement();

        @AttributeDefinition(name = "%tags", cardinality = 0, description = "%tagsDesc", required = false)
        String tags() default "tag1=111,tag2=222";

        @AttributeDefinition(name = "%username", description = "%usernameDesc", required = true)
        String username() default "admin";

        @AttributeDefinition(name = "%password", description = "%passwordDesc", type = AttributeType.PASSWORD, required = false)
        String password();

        @AttributeDefinition(name = "%database", description = "%databaseDesc", required = false)
        String database();

        @AttributeDefinition(name = "%retentionPolicy", description = "%retentionPolicyDesc", required = false)
        String retentionPolicy();

        @AttributeDefinition(name = "%script", cardinality = 0, description = "%scriptDesc", required = false)
        String script() default "";

        @AttributeDefinition(name = "%initScript", cardinality = 0, description = "%initScriptDesc", required = false)
        String initScript() default "";

    }

}
