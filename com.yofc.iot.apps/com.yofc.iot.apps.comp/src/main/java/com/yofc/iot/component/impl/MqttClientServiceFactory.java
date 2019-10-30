package com.yofc.iot.component.impl;

import java.nio.charset.StandardCharsets;

import javax.net.ssl.SSLSocketFactory;

import org.eclipse.kura.KuraConnectException;
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
import org.osgi.framework.BundleContext;
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

import com.yofc.iot.component.impl.MqttClientServiceFactory.Config;
import com.yofc.iot.component.utils.MqttClientConfiguration;
import com.yofc.iot.component.utils.MqttClientConfiguration.PersistenceType;

@Designate(ocd = Config.class, factory = true)
@Component(configurationPolicy = ConfigurationPolicy.REQUIRE)
public class MqttClientServiceFactory implements MqttCallback, ConfigurableComponent {

    private static final Logger logger = LoggerFactory.getLogger(MqttClientServiceFactory.class);
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

    @Activate
    public void start(BundleContext context, Config properties) {
        if (!properties.activate()) {
            stop(context);
            return;
        }
        MqttConnectOptions conOpt = new MqttConnectOptions();
        String clientId = properties.clientId();
        String brokerUrl = properties.brokeUrl();
        String userName = properties.username();
        conOpt.setUserName(userName);
        String password = properties.password();
        try {
            char[] pass = this.cryptoService.decryptAes(password.toCharArray());
            conOpt.setPassword(pass);
        } catch (Exception e) {
            logger.info("Password is not encrypted");
            conOpt.setPassword(password.toCharArray());
        }
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
        PersistenceType persistenceType = null;
        if ("file".equals(sType)) {
            persistenceType = PersistenceType.FILE;
        } else if ("memory".equals(sType)) {
            persistenceType = PersistenceType.MEMORY;
        } else {
            throw new IllegalStateException("Invalid MQTT client configuration: persistenceType: " + persistenceType);
        }

        this.clientConf = new MqttClientConfiguration(brokerUrl, clientId, persistenceType, conOpt);
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
        logger.info("#");
        logger.info("#  Connecting...");

        // connect
        try {
            IMqttToken connectToken = this.mqttClient.connect(this.clientConf.getConnectOptions());
            connectToken.waitForCompletion(getTimeToWaitMillis() * 3);
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
                StringBuffer sb = new StringBuffer();
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
        try {
            logger.info("Closing client...");
            this.mqttClient.setCallback(null);
            this.mqttClient.close();
            logger.info("Closed");
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

    @Modified
    public void update(BundleContext context, Config properties) {
        logger.info("Modified");

    }

    @Deactivate
    public void stop(BundleContext context) {

    }

    @ObjectClassDefinition(id = "com.yofc.iot.component.impl.MqttClientServiceFactory", name = "%name", description = "%description", localization = "OSGI-INF/l10n/com.yofc.iot.component.impl.MqttClientServiceCfg")
    @interface Config {

        @AttributeDefinition(name = "%activate", description = "%activateDesc")
        boolean activate() default false;

        @AttributeDefinition(name = "%brokeUrl", cardinality = 0, description = "%brokeUrlDesc", required = true)
        String brokeUrl() default "mqtt://localhost:1883/";

        @AttributeDefinition(name = "%username", cardinality = 0, description = "%usernameDesc", required = false)
        String username() default "";

        @AttributeDefinition(name = "%password", type = AttributeType.PASSWORD, cardinality = 0, description = "%passwordDesc", required = false)
        String password() default "";

        @AttributeDefinition(name = "%clientId", cardinality = 0, description = "%clientIdDesc", required = false)
        String clientId() default "";

        @AttributeDefinition(name = "%keepAliveInterval", cardinality = 0, description = "%keepAliveIntervalDesc", required = false)
        int keepAliveInterval() default 1000;

        @AttributeDefinition(name = "%connectionTimeout", cardinality = 0, description = "%connectionTimeoutDesc", required = false)
        int connectionTimeout() default 1000;

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
        String sslProtocol() default "TLS1.2";

        @AttributeDefinition(name = "%sslCiphers", cardinality = 0, description = "%sslCiphersDesc", required = false)
        String sslCiphers() default "";

        @AttributeDefinition(name = "%hnVerification", cardinality = 0, description = "%hnVerificationDesc", required = false)
        String hnVerification() default "";

        @AttributeDefinition(name = "%persistenceType", cardinality = 0, description = "%persistenceTypeDesc", required = false, options = {
                @Option(label = "memory", value = "memory"), @Option(label = "fiel", value = "file") })
        String persistenceType() default "memory";
    }

    @Override
    public void connectionLost(Throwable cause) {

    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {

    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {

    }
}
