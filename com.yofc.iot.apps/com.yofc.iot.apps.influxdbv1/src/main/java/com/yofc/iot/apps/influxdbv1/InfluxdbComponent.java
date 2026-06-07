package com.yofc.iot.apps.influxdbv1;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

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

import com.alibaba.fastjson2.JSONObject;
import com.yofc.iot.apps.influxdbv1.InfluxdbComponent.Config;
import com.yofc.vertx.osgi.api.AssetHelp;
import com.yofc.vertx.osgi.api.Utils;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import io.vertx.core.Vertx;
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
    private final Object clientLock = new Object();
    private ExecutorService writeExecutor;
    private final AtomicLong droppedWrites = new AtomicLong();
    @Reference
    private AssetHelp assetHelp;

    public void unbindWireHelperService(final WireHelperService wireHelperService) {
        if (this.wireHelperService == wireHelperService) {
            this.wireHelperService = null;
        }
    }

    @SuppressWarnings("unchecked")
    @Activate
    public void activate(final ComponentContext componentContext, Config config) {
        logger.info("Activating influxdbv1...");
        this.wireSupport = this.wireHelperService.newWireSupport(this,
                (ServiceReference<WireComponent>) componentContext.getServiceReference());
        try {
            updated(config);
        } catch (Exception e) {
            logger.error("active influxdbv1 failed", e);
            deactivate();
            return;
        }

        logger.info("Activating influxdbv1... Done");
    }

    @Deactivate
    public void deactivate() {
        logger.info("Deactivating influxdbv1...");
        if (webClient != null) {
            webClient.close();
            webClient = null;
        }
        if (this.writeExecutor != null) {
            this.writeExecutor.shutdownNow();
            this.writeExecutor = null;
        }
        synchronized (this.clientLock) {
            if (this.client != null) {
                this.client.close();
                this.client = null;
            }
        }
        logger.info("Deactivating influxdbv1... Done");
    }

    @Modified
    public synchronized void updated(final Config config) {
        logger.info("Updating influxdbv1...");
        scriptSource = config.script();
        this.config = config;
        if (this.writeExecutor != null) {
            this.writeExecutor.shutdownNow();
        }
        this.writeExecutor = createWriteExecutor(config.writeQueueSize());
        synchronized (this.clientLock) {
            if (this.client != null) {
                this.client.close();
                this.client = null;
            }
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
        synchronized (this.clientLock) {
            this.client = InfluxDBFactory.connect(config.url(), config.username(), password);
            this.client.setDatabase(config.database());
            this.client.setRetentionPolicy(config.retentionPolicy());
        }

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
                if (script != null && !script.getMetaClass().respondsTo(script, "destroy").isEmpty()) {
                    try {
                        script.invokeMethod("destroy", null);
                    } catch (Exception e) {
                        logger.error("Script destroy method error", e);
                    }
                }
                scriptSource = scriptSource + "\r\n" + config.initScript();
            }
            try {
                script = shell.parse(scriptSource);
            } catch (Exception e) {
                logger.error("Script parse error", e);
                scriptSource = null;
                return;
            }
            if (!"".equals(config.initScript()) && !script.getMetaClass().respondsTo(script, "init").isEmpty()) {
                try {
                    script.invokeMethod("init", null);
                } catch (Exception e) {
                    logger.error("Script init method error", e);
                }
            }
            logger.info("Updating influxdbv1... Done");
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public synchronized void onWireReceive(Object wireEnvelope) {
        if (client == null) {
            logger.error("influxdb initial error!");
            return;
        }
        // Note: client.ping() removed — it blocks for OkHttp timeout when the
        // server is unreachable, jamming the wire pool at 100ms ticks. Async write
        // failures surface via InfluxDB's own logger (com.influxdb level=error in
        // log4j) instead.
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
                if (output == null) {
                    return;
                }

            } catch (Exception e) {
                logger.warn("Failed to execute script", e);
            }
        }
        if (output == null) {
            output = wireEnvelope;
        }
        this.wireSupport.emit(output);
        submitInfluxWrite(output);
    }

    @SuppressWarnings("unchecked")
    private void saveToInfluxdb(Object wires) {
        synchronized (this.clientLock) {
            if (this.client == null || this.config == null) {
                logger.warn("InfluxDB v1 write skipped: component is not initialized");
                return;
            }
            Point.Builder pointBuilder = Point.measurement(this.config.measurement());
            pointBuilder.tag(parseTags(this.config.tags()));
            Map<String, Object> result;
            if (wires instanceof WireEnvelope) {
                result = Utils.toMap(((WireEnvelope) wires).getRecords());
            } else if (List.class.isAssignableFrom(wires.getClass()) && !((List<?>) wires).isEmpty()
                    && ((List<?>) wires).get(0) instanceof WireRecord) {
                result = Utils.toMap((List<WireRecord>) wires);
            } else {
                result = JSONObject.from(wires);
            }

            result.remove("assetName");
            pointBuilder.fields(result);

            this.client.write(pointBuilder.build());
        }

    }

    private ExecutorService createWriteExecutor(int queueSize) {
        final int boundedQueueSize = Math.max(1, queueSize);
        final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(boundedQueueSize), r -> {
                    Thread thread = new Thread(r, "InfluxdbV1Component-Writer");
                    thread.setDaemon(true);
                    return thread;
                });
        executor.setRejectedExecutionHandler((r, rejectedExecutor) -> {
            long dropped = this.droppedWrites.incrementAndGet();
            if (dropped == 1 || dropped % 1000 == 0) {
                logger.warn("InfluxDB v1 write queue full; droppedWrites={}, queued={}/{}", dropped,
                        rejectedExecutor.getQueue().size(), boundedQueueSize);
            }
        });
        return executor;
    }

    private void submitInfluxWrite(Object output) {
        ExecutorService executor = this.writeExecutor;
        if (executor == null || executor.isShutdown()) {
            logger.warn("InfluxDB v1 write skipped: write executor is not running");
            return;
        }
        executor.execute(() -> {
            try {
                saveToInfluxdb(output);
            } catch (Exception e) {
                logger.warn("Failed to write to InfluxDB v1", e);
            }
        });
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

        @AttributeDefinition(name = "%measurement", description = "%measurementDesc", required = true)
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

        @AttributeDefinition(name = "%writeQueueSize", description = "%writeQueueSizeDesc", required = false)
        int writeQueueSize() default 1024;

    }

}
