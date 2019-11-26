package com.yofc.iot.component.api;

import org.eclipse.kura.KuraException;

public interface MqttClientService {

    public Integer publish(String topic, int qos, boolean retain, Object message) throws KuraException;

    public void subscribe(String topic, int qos) throws KuraException;

    public void subscribe(String topic[], int qos[]) throws KuraException;
}
