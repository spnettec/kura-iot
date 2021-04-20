package com.yofc.iot.component.utils;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;

public class MqttClientConfiguration {

  private final String brokerUrl;
  private final String clientId;
  private final PersistenceType persistenceType;
  private final MqttConnectOptions connectOptions;

  public MqttClientConfiguration(
      String brokerUrl,
      String clientId,
      PersistenceType persistenceType,
      MqttConnectOptions connectOptions) {
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

  public MqttConnectOptions getConnectOptions() {
    return this.connectOptions;
  }

  public enum PersistenceType {
    FILE,
    MEMORY
  }
}
