package com.yofc.iot.component.api;

import org.eclipse.kura.KuraException;

import java.util.function.BiConsumer;

public interface MqttClientService {

    Integer publish(String topic, int qos, boolean retain, Object message) throws KuraException;

    void subscribe(String topic, int qos, BiConsumer<String, Object> consumer) throws KuraException;

    boolean isConnected();
}
