package com.yofc.iot.apps.influxdb;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.crypto.CryptoService;
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

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.QueryApi;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.yofc.iot.apps.influxdb.InfluxdbReaderComponent.Config;
import com.yofc.vertx.osgi.api.Utils;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import okhttp3.OkHttpClient;

@Designate(factory = true, ocd = Config.class)
@Component(immediate = true, enabled = true, configurationPolicy = ConfigurationPolicy.REQUIRE, service = {
        ConfigurableComponent.class, WireComponent.class, WireEmitter.class, WireReceiver.class, Producer.class,
        Consumer.class }, property = { "kura.ui.service.hide:Boolean=true" })
public class InfluxdbReaderComponent implements WireEmitter, WireReceiver, ConfigurableComponent {

    private static final Logger logger = LoggerFactory.getLogger(InfluxdbReaderComponent.class);

    private InfluxDBClient client;
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

    public void unbindWireHelperService(final WireHelperService wireHelperService) {
        if (this.wireHelperService == wireHelperService) {
            this.wireHelperService = null;
        }
    }

    @SuppressWarnings("unchecked")
    @Activate
    public void activate(final ComponentContext componentContext, Config config) {
        logger.info("Activating InfluxDB v2 reader...");
        this.wireSupport = this.wireHelperService.newWireSupport(this,
                (ServiceReference<WireComponent>) componentContext.getServiceReference());
        try {
            updated(config);
        } catch (Exception e) {
            logger.error("Activate InfluxDB v2 reader failed", e);
            deactivate();
            return;
        }
        logger.info("Activating InfluxDB v2 reader... Done");
    }

    @Deactivate
    public void deactivate() {
        logger.info("Deactivating InfluxDB v2 reader...");
        if (webClient != null) {
            webClient.close();
            webClient = null;
        }
        synchronized (this.clientLock) {
            if (this.client != null) {
                this.client.close();
                this.client = null;
            }
        }
        logger.info("Deactivating InfluxDB v2 reader... Done");
    }

    @Modified
    public synchronized void updated(final Config config) {
        logger.info("Updating InfluxDB v2 reader...");
        scriptSource = config.script();
        this.config = config;
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

        OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .dns(new OkHttpDns(3L))
                .retryOnConnectionFailure(false);

        com.influxdb.client.InfluxDBClientOptions.Builder influxDbBuilder =
                com.influxdb.client.InfluxDBClientOptions.builder()
                        .url(config.url())
                        .okHttpClient(okHttpClientBuilder);

        if (config.token() != null && !config.token().isEmpty()) {
            char[] token;
            try {
                token = this.cryptoService.decryptAes(config.token().toCharArray());
            } catch (Exception e) {
                logger.info("Token is not encrypted");
                token = config.token().toCharArray();
            }
            influxDbBuilder.authenticateToken(token).org(config.organization()).bucket(config.bucket());
        } else if (config.database() != null) {
            influxDbBuilder.org("-")
                    .authenticateToken(String.format("%s:%s",
                            config.username() == null ? "" : config.username(),
                            password == null ? "" : password).toCharArray())
                    .bucket(String.format("%s/%s", config.database(),
                            config.retentionPolicy() == null ? "" : config.retentionPolicy()));
        } else {
            influxDbBuilder.authenticate(config.username(), password.toCharArray());
        }

        synchronized (this.clientLock) {
            this.client = InfluxDBClientFactory.create(influxDbBuilder.build());
        }

        // init Groovy (always if script or initScript is set)
        initGroovy(config);

        logger.info("Updating InfluxDB v2 reader... Done");
    }

    private void initGroovy(Config config) {
        final boolean hasScript = scriptSource != null && !scriptSource.isEmpty();
        final boolean hasInit = config.initScript() != null && !config.initScript().isEmpty();

        if (!hasScript && !hasInit) {
            return;
        }

        if (webClient != null) {
            webClient.close();
        }
        webClient = WebClient.create(vertx);

        Binding binding = new Binding();
        GroovyShell shell = new GroovyShell(binding);
        binding.setVariable("logger", logger);
        binding.setVariable("webClient", webClient);
        binding.setVariable("vertx", vertx);
        binding.setVariable("dbClient", client);

        String source = scriptSource != null ? scriptSource : "";
        if (hasInit) {
            if (script != null && !script.getMetaClass().respondsTo(script, "destroy").isEmpty()) {
                try {
                    script.invokeMethod("destroy", null);
                } catch (Exception e) {
                    logger.error("Script destroy method error", e);
                }
            }
            source = source + "\r\n" + config.initScript();
        }
        try {
            script = shell.parse(source);
        } catch (Exception e) {
            logger.error("Script parse error", e);
            scriptSource = null;
            return;
        }
        if (hasInit && !script.getMetaClass().respondsTo(script, "init").isEmpty()) {
            try {
                script.invokeMethod("init", null);
            } catch (Exception e) {
                logger.error("Script init method error", e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public synchronized void onWireReceive(Object wireEnvelope) {
        if (client == null) {
            logger.error("InfluxDB v2 reader: client is not initialized");
            return;
        }

        // Step 1: auto-query (simple mode) or use wire input (pure script mode)
        Object data = autoQuery();
        final boolean hasScript = scriptSource != null && !scriptSource.isEmpty();

        if (data == null && !hasScript) {
            // No query result and no script — nothing to emit
            return;
        }

        // Step 2: run Groovy script to post-process (if configured)
        Object output;
        if (hasScript) {
            try {
                Object scriptInput = (data != null) ? data : wireToMap(wireEnvelope);
                script.setProperty("input", scriptInput);
                script.run();
                output = script.getProperty("output");
            } catch (Exception e) {
                logger.warn("InfluxDB v2 reader: script execution failed", e);
                return;
            }
        } else {
            output = data;
        }

        if (output != null) {
            this.wireSupport.emit(output);
        }
    }

    /**
     * Simple mode: build a Flux query from {@code measurement} / {@code fields} /
     * {@code range} / {@code bucket}, execute it, and return a
     * {@code Map<String, Object>} keyed by field name. Returns null if not
     * configured or query yields no data.
     */
    private Map<String, Object> autoQuery() {
        if (config == null) {
            return null;
        }
        final String measurement = config.measurement();
        final String fieldsStr = config.fields();
        final String range = config.range();
        final String bucket = config.bucket();

        if (measurement == null || measurement.isEmpty() || fieldsStr == null || fieldsStr.isEmpty()) {
            // Simple mode not configured — caller will fall back to pure script or skip
            return null;
        }
        if (bucket == null || bucket.isEmpty()) {
            logger.warn("InfluxDB v2 reader: bucket must be configured");
            return null;
        }

        final List<String> fieldList = Arrays.stream(fieldsStr.split("\\s*,\\s*"))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        if (fieldList.isEmpty()) {
            return null;
        }

        final String fieldFilter = fieldList.stream()
                .map(f -> "r._field == \"" + f + "\"")
                .collect(Collectors.joining(" or "));

        final String flux = String.format(
                "from(bucket: \"%s\") |> range(start: %s) |> filter(fn: (r) => r._measurement == \"%s\") |> filter(fn: (r) => %s) |> last()",
                bucket, range, measurement, fieldFilter);

        logger.debug("InfluxDB v2 reader: Flux = {}", flux);

        try {
            final QueryApi queryApi = client.getQueryApi();
            final List<FluxTable> tables = queryApi.query(flux);
            final Map<String, Object> result = new HashMap<>();
            for (FluxTable table : tables) {
                for (FluxRecord record : table.getRecords()) {
                    if (record.getField() != null) {
                        result.put(record.getField(), record.getValue());
                    }
                    if (record.getTime() != null) {
                        result.putIfAbsent("_time", record.getTime().toString());
                    }
                }
            }
            if (result.isEmpty()) {
                logger.debug("InfluxDB v2 reader: query returned no data");
                return null;
            }
            return result;
        } catch (Exception e) {
            logger.error("InfluxDB v2 reader: auto-query failed", e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Object wireToMap(Object wireEnvelope) {
        if (wireEnvelope instanceof WireEnvelope wireenvelope) {
            return Utils.toMap(wireenvelope.getRecords());
        }
        if (List.class.isAssignableFrom(wireEnvelope.getClass())
                && !((List<?>) wireEnvelope).isEmpty()
                && ((List<?>) wireEnvelope).get(0) instanceof WireRecord) {
            return Utils.toMap((List<WireRecord>) wireEnvelope);
        }
        return wireEnvelope;
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

    @ObjectClassDefinition(id = "com.yofc.iot.apps.influxdb.InfluxdbReaderComponent",
            name = "%name", description = "%description",
            localization = "OSGI-INF/l10n/com.yofc.iot.apps.influxdb.InfluxdbReaderComponentCfg")
    @interface Config {

        @AttributeDefinition(name = "%url", description = "%urlDesc")
        String url();

        @AttributeDefinition(name = "%token", description = "%tokenDesc",
                type = AttributeType.PASSWORD, required = false)
        String token();

        @AttributeDefinition(name = "%organization", description = "%organizationDesc", required = false)
        String organization();

        @AttributeDefinition(name = "%bucket", description = "%bucketDesc", required = false)
        String bucket();

        @AttributeDefinition(name = "%username", description = "%usernameDesc", required = false)
        String username();

        @AttributeDefinition(name = "%password", description = "%passwordDesc",
                type = AttributeType.PASSWORD, required = false)
        String password();

        @AttributeDefinition(name = "%database", description = "%databaseDesc", required = false)
        String database();

        @AttributeDefinition(name = "%retentionPolicy", description = "%retentionPolicyDesc", required = false)
        String retentionPolicy();

        @AttributeDefinition(name = "%measurement", description = "%measurementDesc",
                required = false)
        String measurement() default "";

        @AttributeDefinition(name = "%fields", description = "%fieldsDesc",
                required = false)
        String fields() default "";

        @AttributeDefinition(name = "%range", description = "%rangeDesc",
                required = false)
        String range() default "-5m";

        @AttributeDefinition(name = "%script", cardinality = 0,
                description = "%scriptDesc", required = false)
        String script() default "";

        @AttributeDefinition(name = "%initScript", cardinality = 0,
                description = "%initScriptDesc", required = false)
        String initScript() default "";
    }
}
