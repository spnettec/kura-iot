package com.yofc.iot.component.utils;

import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;

public class MqttV5ClientConfiguration {

    private final String brokerUrl;
    private final String clientId;
    private final PersistenceType persistenceType;
    private final MqttConnectionOptions connectOptions;

    public MqttV5ClientConfiguration(String brokerUrl, String clientId, PersistenceType persistenceType,
            MqttConnectionOptions connectOptions) {
        super();
        this.brokerUrl = brokerUrl;
        this.clientId = clientId;
        this.persistenceType = persistenceType;
        this.connectOptions = connectOptions;
    }

    public String getBrokerUrl() {
        return this.brokerUrl;
    }

    public String getClientId() {
        return this.clientId;
    }

    public PersistenceType getPersistenceType() {
        return this.persistenceType;
    }

    public MqttConnectionOptions getConnectOptions() {
        return this.connectOptions;
    }

    public enum PersistenceType {
        FILE,
        MEMORY
    }
}
