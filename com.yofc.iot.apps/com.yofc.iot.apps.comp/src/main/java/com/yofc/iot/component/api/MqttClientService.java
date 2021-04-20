package com.yofc.iot.component.api;

import org.eclipse.kura.KuraException;

public interface MqttClientService {

  Integer publish(String topic, int qos, boolean retain, Object message) throws KuraException;

  void subscribe(String topic, int qos) throws KuraException;

  void subscribe(String[] topic, int[] qos) throws KuraException;
}
