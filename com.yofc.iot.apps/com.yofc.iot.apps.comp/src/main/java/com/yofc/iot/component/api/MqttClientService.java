package com.yofc.iot.component.api;

import org.eclipse.kura.KuraException;

import io.reactivex.functions.BiConsumer;

public interface MqttClientService {

    Integer publish(String topic, int qos, boolean retain, Object message) throws KuraException;

    void subscribe(String topic, int qos) throws KuraException;

    void subscribe(String[] topic, int[] qos) throws KuraException;

    void messageArrived(BiConsumer<String, Object> listerner);

    boolean isConnected();
}
