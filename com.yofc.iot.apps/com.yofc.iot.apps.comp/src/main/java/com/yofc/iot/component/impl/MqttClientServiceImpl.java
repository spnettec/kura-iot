package com.yofc.iot.component.impl;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLSocketFactory;

import org.eclipse.kura.KuraConnectException;
import org.eclipse.kura.KuraException;
import org.eclipse.kura.KuraNotConnectedException;
import org.eclipse.kura.KuraTimeoutException;
import org.eclipse.kura.KuraTooManyInflightMessagesException;
import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.crypto.CryptoService;
import org.eclipse.kura.ssl.SslManagerService;
import org.eclipse.kura.system.SystemService;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClientPersistence;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;
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
import org.osgi.service.metatype.annotations.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSON;
import com.yofc.iot.component.api.MqttClientService;
import com.yofc.iot.component.impl.MqttClientServiceImpl.Config;
import com.yofc.iot.component.utils.MqttClientConfiguration;
import com.yofc.iot.component.utils.MqttClientConfiguration.PersistenceType;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;

@Designate(ocd = Config.class, factory = true)
@Component(configurationPolicy = ConfigurationPolicy.REQUIRE, immediate = true)
public class MqttClientServiceImpl implements MqttCallback, MqttClientService, ConfigurableComponent {

    private static final Logger logger = LoggerFactory.getLogger(MqttClientServiceImpl.class);
    private MqttAsyncClient mqttClient;
    private MqttClientConfiguration clientConf;
    @Reference
    private CryptoService cryptoService;
    @Reference
    private SslManagerService sslManagerService;
    @Reference
    private SystemService systemService;

    private PersistenceType persistenceType;

    private MqttClientPersistence persistence;
    private Config preConfig = null;

    private List<String> registerTopcs = new ArrayList<>();
    protected ScheduledExecutorService connectionMonitorExecutor;
    private ScheduledFuture<?> connectionMonitorFuture;

    @Activate
    public void start(Config properties) throws KuraConnectException {
        this.preConfig = properties;
        this.connectionMonitorExecutor = Executors.newSingleThreadScheduledExecutor();
        updateConfig(properties);
    }

    @Modified
    public void update(Config properties) throws KuraConnectException {
        if (this.preConfig != null && properties.toString().equals(this.preConfig.toString()))
            return;
        closeMqttClient();
        updateConfig(properties);
        this.preConfig = properties;
    }

    @Deactivate
    public void stop() {
        closeMqttClient();
        stopConnectionMonitorTask();
    }

    public boolean isConnected() {
        if (this.mqttClient != null) {
            return this.mqttClient.isConnected();
        }
        return false;
    }

    private long getTimeToWaitMillis() {
        return this.clientConf.getConnectOptions().getConnectionTimeout() * 1000L;
    }

    public synchronized void connect() throws KuraConnectException {
        // We treat this as an application bug.
        if (isConnected()) {
            logger.error("already connected");
            throw new IllegalStateException("already connected");
        }

        // Attempt to setup the MQTT session
        setupMqttSession();

        if (this.mqttClient == null) {
            logger.error("invalid configration");
            throw new IllegalStateException("invalid configration");
        }

        logger.info("# ------------------------------------------------------------");
        logger.info("#  Connection Properties");
        logger.info("#  broker    = {}", this.clientConf.getBrokerUrl());
        logger.info("#  clientId  = {}", this.clientConf.getClientId());
        logger.info("#  username  = {}", this.clientConf.getConnectOptions().getUserName());
        logger.info("#  password  = XXXXXXXXXXXXXX");
        logger.info("#  keepAlive = {}", this.clientConf.getConnectOptions().getKeepAliveInterval());
        logger.info("#  timeout   = {}", this.clientConf.getConnectOptions().getConnectionTimeout());
        logger.info("#  cleanSession    = {}", this.clientConf.getConnectOptions().isCleanSession());
        logger.info("#  MQTT version    = {}",
                getMqttVersionLabel(this.clientConf.getConnectOptions().getMqttVersion()));
        logger.info("#  willDestination = {}", this.clientConf.getConnectOptions().getWillDestination());
        logger.info("#  willMessage     = {}", this.clientConf.getConnectOptions().getWillMessage());
        logger.info("#  Connecting...");

        // connect
        try {
            IMqttToken connectToken = this.mqttClient.connect(this.clientConf.getConnectOptions());
            connectToken.waitForCompletion(getTimeToWaitMillis() * 3);
            registerTopcs.clear();
            logger.info("#  Connected!");
            logger.info("# ------------------------------------------------------------");
        } catch (MqttException e) {
            logger.warn("xxxxx  Connect failed. Forcing disconnect. xxxxx");
            closeMqttClient();
            throw new KuraConnectException(e, "Cannot connect");
        } finally {
            //
        }
    }

    private void setupMqttSession() {
        if (this.clientConf == null) {
            throw new IllegalStateException("Invalid client configuration");
        }
        if (this.mqttClient != null) {
            String brokerUrl = this.mqttClient.getServerURI();
            String clientId = this.mqttClient.getClientId();
            if (!(brokerUrl.equals(this.clientConf.getBrokerUrl()) && clientId.equals(this.clientConf.getClientId())
                    && this.persistenceType == this.clientConf.getPersistenceType())) {
                closeMqttClient();
            }
        }
        if (this.mqttClient == null) {
            logger.info("Creating a new client instance");
            PersistenceType persistenceType = this.clientConf.getPersistenceType();
            if (persistenceType == PersistenceType.MEMORY) {
                logger.info("Using memory persistence for in-flight messages");
                this.persistence = new MemoryPersistence();
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append(this.systemService.getKuraDataDirectory()).append(this.systemService.getFileSeparator())
                        .append("paho-persistence");
                String dir = sb.toString();
                logger.info("Using file persistence for in-flight messages: {}", dir);
                if (this.persistence != null) {
                    try {
                        this.persistence.close();
                    } catch (MqttPersistenceException e) {
                    }
                }
                this.persistence = new MqttDefaultFilePersistence(dir);
            }
            try {
                MqttAsyncClient newMqttClient = new MqttAsyncClient(this.clientConf.getBrokerUrl(),
                        this.clientConf.getClientId(), this.persistence);
                newMqttClient.setCallback(this);
                this.mqttClient = newMqttClient;
            } catch (MqttException e) {
                logger.error("Client instantiation failed", e);
                throw new IllegalStateException("Client instantiation failed");
            }
            this.persistenceType = persistenceType;
        }
    }

    private void closeMqttClient() {
        if (this.mqttClient != null)
            try {
                logger.info("Closing client...");
                if (!this.registerTopcs.isEmpty())
                    this.mqttClient.unsubscribe(this.registerTopcs.toArray(new String[0]));
                this.mqttClient.setCallback(null);
                if (this.mqttClient.isConnected()) {
                    this.mqttClient.disconnect(0);
                    this.mqttClient.close();
                    logger.info("Close Client");
                }
            } catch (MqttException e) {
                logger.warn("Cannot close client", e);
            } finally {
                this.mqttClient = null;
            }
    }

    private static String getMqttVersionLabel(int mqttVersion) {
        switch (mqttVersion) {
        case MqttConnectOptions.MQTT_VERSION_3_1:
            return "3.1";
        case MqttConnectOptions.MQTT_VERSION_3_1_1:
            return "3.1.1";
        default:
            return String.valueOf(mqttVersion);
        }
    }

    private void updateConfig(Config properties) throws KuraConnectException {
        if (!properties.activate()) {
            closeMqttClient();
            return;
        }
        MqttConnectOptions conOpt = new MqttConnectOptions();
        String clientId = properties.clientId();
        String brokerUrl = properties.brokeUrl();
        String userName = properties.username();
        Boolean isPostProcess = properties.postProcess();
        String scriptSource = properties.postProcessCode();
        String password = properties.password();
        try {
            char[] pass = this.cryptoService.decryptAes(password.toCharArray());
            password = new String(pass);
        } catch (Exception e) {
            logger.info("Password is not encrypted");
        }
        if (isPostProcess != null && isPostProcess && scriptSource != null && !scriptSource.equals("")) {
            Binding binding = new Binding();
            GroovyShell shell = new GroovyShell(binding);
            binding.setVariable("logger", logger);
            binding.setProperty("userName", userName);
            binding.setProperty("passwordString", password);
            binding.setProperty("clientId", clientId);
            try {
                Script script = shell.parse(scriptSource);
                script.run();
                userName = (String) script.getProperty("userName");
                password = (String) script.getProperty("passwordString");
                clientId = (String) script.getProperty("clientId");
            } catch (Exception e) {
                logger.error("Script parse error:{}", e);
            }
        }
        conOpt.setUserName(userName);
        conOpt.setPassword(password.toCharArray());
        conOpt.setKeepAliveInterval(properties.keepAliveInterval());
        conOpt.setConnectionTimeout(properties.connectionTimeout());

        conOpt.setCleanSession(properties.cleanSession());

        conOpt.setMqttVersion(properties.mqttVersion());
        String willTopic = properties.willTopic();
        if (willTopic != null && !willTopic.equals("")) {
            int willQos = properties.willQos();
            boolean willRetain = properties.willRetain();
            String willPayload = properties.willPayload();
            byte[] payload = {};
            if (willPayload != null && !willPayload.isEmpty()) {
                payload = willPayload.getBytes(StandardCharsets.UTF_8);
            }

            conOpt.setWill(willTopic, payload, willQos, willRetain);
        }
        // SSL
        if (brokerUrl.startsWith("ssl") || brokerUrl.startsWith("wss")) {
            try {
                String alias = properties.sslCertAlias();

                String protocol = properties.sslProtocol();
                String ciphers = properties.sslCiphers();
                String hnVerification = properties.hnVerification();

                SSLSocketFactory ssf;
                if ("use-ssl-service-config".equals(hnVerification)) {
                    ssf = this.sslManagerService.getSSLSocketFactory(protocol, ciphers, null, null, null, alias);
                } else {
                    ssf = this.sslManagerService.getSSLSocketFactory(protocol, ciphers, null, null, null, alias,
                            Boolean.valueOf(hnVerification));
                }

                conOpt.setSocketFactory(ssf);
            } catch (Exception e) {
                logger.error("SSL setup failed", e);
                throw new IllegalStateException("SSL setup failed");
            }
        }
        String sType = properties.persistenceType();
        PersistenceType pType = null;
        if ("file".equals(sType)) {
            pType = PersistenceType.FILE;
        } else if ("memory".equals(sType)) {
            pType = PersistenceType.MEMORY;
        } else {
            throw new IllegalStateException("Invalid MQTT client configuration: persistenceType: " + pType);
        }
        this.clientConf = new MqttClientConfiguration(brokerUrl, clientId, pType, conOpt);
        if (this.preConfig.autoConnect())
            startConnectionMonitorTask();
        else
            this.connect();
    }

    private void checkConnected() throws KuraNotConnectedException {
        if (!isConnected()) {
            if (this.connectionMonitorFuture == null || this.connectionMonitorFuture.isDone()) {
                startConnectionMonitorTask();
                throw new KuraNotConnectedException("client not connected, can't publish");
            } else
                throw new IllegalStateException("ConnectionMonitorTask is running, can't publish");

        }
    }

    @Override
    public Integer publish(String topic, int qos, boolean retain, Object wireRecords) throws KuraException {
        checkConnected();
        byte[] payload = JSON.toJSONBytes(wireRecords);
        MqttMessage message = new MqttMessage();
        message.setPayload(payload);
        message.setQos(qos);
        message.setRetained(retain);
        Integer messageId = null;
        try {
            IMqttDeliveryToken token = this.mqttClient.publish(topic, message);
            logger.debug("Published message with ID: {}", token.getMessageId());
            if (qos > 0) {
                messageId = Integer.valueOf(token.getMessageId());
            }
        } catch (MqttPersistenceException e) {
            // This is probably an unrecoverable internal error
            logger.error("Cannot publish on topic: {}", topic, e);
            throw new IllegalStateException("Cannot publish on topic: " + topic);
        } catch (MqttException e) {
            if (e.getReasonCode() == MqttException.REASON_CODE_MAX_INFLIGHT) {
                logger.info("Too many inflight messages");
                throw new KuraTooManyInflightMessagesException(e, "Too many in-fligh messages");
            } else {
                logger.error("Cannot publish on topic: " + topic, e);
                throw KuraException.internalError(e, "Cannot publish on topic: " + topic);
            }
        }
        return messageId;
    }

    @Override
    public void subscribe(String topicFilter, int qos) throws KuraException {
        checkConnected();
        logger.info("Subscribing to topic: {} with QoS: {}", topicFilter, qos);

        try {
            IMqttToken token = this.mqttClient.subscribe(topicFilter, qos);
            token.waitForCompletion(getTimeToWaitMillis());
            registerTopcs.add(topicFilter);
        } catch (MqttException e) {
            if (e.getReasonCode() == MqttException.REASON_CODE_CLIENT_TIMEOUT) {
                logger.warn("Timeout subscribing to topic: {}", topicFilter);
                throw new KuraTimeoutException("Timeout subscribing to topic: " + topicFilter, e);
            } else {
                logger.error("Cannot subscribe to topic: " + topicFilter, e);
                throw KuraException.internalError(e, "Cannot subscribe to topic: " + topicFilter);
            }
        }
    }

    @Override
    public void subscribe(String topicFilters[], int qos[]) throws KuraException {
        checkConnected();
        logger.info("Subscribing to topic: {} with QoS: {}", topicFilters, qos);

        try {
            IMqttToken token = this.mqttClient.subscribe(topicFilters, qos);
            token.waitForCompletion(getTimeToWaitMillis());
            registerTopcs.addAll(Arrays.asList(topicFilters));
        } catch (MqttException e) {
            if (e.getReasonCode() == MqttException.REASON_CODE_CLIENT_TIMEOUT) {
                logger.warn("Timeout subscribing to topic: {}", Arrays.asList(topicFilters));
                throw new KuraTimeoutException(
                        "Timeout subscribing to topic: " + Arrays.asList(topicFilters).toString(), e);
            } else {
                logger.error("Cannot subscribe to topic: " + topicFilters, e);
                throw KuraException.internalError(e, "Cannot subscribe to topic: " + Arrays.asList(topicFilters));
            }
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        logger.error("connection lost", cause);
        stopConnectionMonitorTask(); // Just in case...
        startConnectionMonitorTask();
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        logger.info("message received, topic:{}, message:{}", topic, message);
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        logger.debug("message delivery Complete token:{}", token);
    }

    private boolean startConnectionMonitorTask() {
        if (this.connectionMonitorFuture != null && !this.connectionMonitorFuture.isDone()) {
            logger.error("Reconnect task already running");
            throw new IllegalStateException("Reconnect task already running");
        }
        boolean autoConnect = this.preConfig.autoConnect();
        int reconnectInterval = this.preConfig.connectDelay();
        if (autoConnect) {
            int maxDelay = reconnectInterval / 5;
            maxDelay = maxDelay > 0 ? maxDelay : 1;
            int initialDelay = new Random().nextInt(maxDelay);
            logger.info("Starting reconnect task with initial delay {}", initialDelay);
            this.connectionMonitorFuture = this.connectionMonitorExecutor.scheduleAtFixedRate(new Runnable() {

                @Override
                public void run() {
                    String originalName = Thread.currentThread().getName();
                    Thread.currentThread().setName("DataServiceImpl:ReconnectTask");
                    boolean connected = false;
                    try {
                        if (MqttClientServiceImpl.this.isConnected())
                            logger.info("Already connected. Reconnect task will be terminated.");
                        else {
                            MqttClientServiceImpl.this.connect();
                            logger.info("Connected. Reconnect task will be terminated.");
                        }
                        connected = true;
                    } catch (KuraConnectException e) {
                        if (isAuthenticationException(e))
                            throw new RuntimeException("Authentication Exception. Reconnect task will be terminated.");
                    } catch (Error e) {
                        logger.error("Unexpected Error. Task will be terminated", e);
                        throw e;
                    } finally {
                        Thread.currentThread().setName(originalName);
                        if (connected) {
                            // Throwing an exception will suppress subsequent executions of this periodic task.
                            throw new RuntimeException("Connected. Reconnect task will be terminated.");
                        }
                    }
                }

                private boolean isAuthenticationException(KuraConnectException e) {
                    boolean authenticationException = false;
                    if (e.getCause() instanceof MqttException) {
                        MqttException mqttException = (MqttException) e.getCause();
                        if (mqttException.getReasonCode() == MqttException.REASON_CODE_FAILED_AUTHENTICATION
                                || mqttException.getReasonCode() == MqttException.REASON_CODE_INVALID_CLIENT_ID
                                || mqttException.getReasonCode() == MqttException.REASON_CODE_NOT_AUTHORIZED) {
                            logger.info("Authentication exception encountered.");
                            authenticationException = true;
                        }
                    }
                    return authenticationException;
                }

            }, initialDelay, reconnectInterval, TimeUnit.SECONDS);
        }
        return autoConnect;
    }

    private void stopConnectionMonitorTask() {
        if (this.connectionMonitorFuture != null && !this.connectionMonitorFuture.isDone()) {

            logger.info("Reconnect task running. Stopping it");

            this.connectionMonitorFuture.cancel(true);
        }
    }

    @ObjectClassDefinition(id = "com.yofc.iot.component.impl.MqttClientServiceImpl", name = "%name", description = "%description", localization = "OSGI-INF/l10n/com.yofc.iot.component.impl.MqttClientServiceCfg")
    @interface Config {

        @AttributeDefinition(name = "%activate", description = "%activateDesc")
        boolean activate() default false;

        @AttributeDefinition(name = "%brokerUrl", cardinality = 0, description = "%brokerUrlDesc", required = true)
        String brokeUrl() default "mqtt://localhost:1883/";

        @AttributeDefinition(name = "%username", cardinality = 0, description = "%usernameDesc", required = false)
        String username() default "";

        @AttributeDefinition(name = "%password", type = AttributeType.PASSWORD, cardinality = 0, description = "%passwordDesc", required = false)
        String password() default "";

        @AttributeDefinition(name = "%clientId", cardinality = 0, description = "%clientIdDesc", required = false)
        String clientId() default "";

        @AttributeDefinition(name = "%postProcess", description = "%postProcessDesc")
        boolean postProcess() default false;

        @AttributeDefinition(name = "%postProcessCode", cardinality = 0, description = "%postProcessCodeDesc", required = false)
        String postProcessCode() default "//userName  passwordString  clientId";

        @AttributeDefinition(name = "%keepAliveInterval", cardinality = 0, description = "%keepAliveIntervalDesc", required = false)
        int keepAliveInterval() default 30;

        @AttributeDefinition(name = "%connectionTimeout", cardinality = 0, description = "%connectionTimeoutDesc", required = false)
        int connectionTimeout() default 10;

        @AttributeDefinition(name = "%cleanSession", description = "%cleanSessionDesc")
        boolean cleanSession() default true;

        @AttributeDefinition(name = "%mqttVersion", cardinality = 0, description = "%mqttVersionDesc", required = false, options = {
                @Option(label = "3.1", value = "3"), @Option(label = "3.1.1", value = "4") })
        int mqttVersion() default 4;

        @AttributeDefinition(name = "%willTopic", cardinality = 0, description = "%willTopicDesc", required = false)
        String willTopic() default "";

        @AttributeDefinition(name = "%willQos", cardinality = 0, description = "%willQosDesc", required = false)
        int willQos() default 0;

        @AttributeDefinition(name = "%willRetain", description = "%willRetainDesc")
        boolean willRetain() default false;

        @AttributeDefinition(name = "%willPayload", cardinality = 0, description = "%willPayloadDesc", required = false)
        String willPayload() default "";

        @AttributeDefinition(name = "%sslCertAlias", cardinality = 0, description = "%sslCertAliasDesc", required = false)
        String sslCertAlias() default "";

        @AttributeDefinition(name = "%sslProtocol", cardinality = 0, description = "%sslProtocolDesc", required = false)
        String sslProtocol() default "TLSv1.2";

        @AttributeDefinition(name = "%sslCiphers", cardinality = 0, description = "%sslCiphersDesc", required = false)
        String sslCiphers() default "";

        @AttributeDefinition(name = "%hnVerification", cardinality = 0, description = "%hnVerificationDesc", required = false)
        String hnVerification() default "";

        @AttributeDefinition(name = "%persistenceType", cardinality = 0, description = "%persistenceTypeDesc", required = false, options = {
                @Option(label = "memory", value = "memory"), @Option(label = "file", value = "file") })
        String persistenceType() default "memory";

        @AttributeDefinition(name = "%autoConnect", description = "%autoConnectDesc")
        boolean autoConnect() default false;

        @AttributeDefinition(name = "%connectDelay", cardinality = 0, description = "%connectDelayDesc", required = false)
        int connectDelay() default 60;
    }
}
