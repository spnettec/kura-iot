package com.yofc.iot.component.api;

import java.util.function.BiConsumer;

import org.eclipse.kura.KuraException;

public interface MqttClientService {

    void publish(String topic, int qos, boolean retain, Object message) throws KuraException;

    void subscribe(String topic, int qos, BiConsumer<String, Object> consumer) throws KuraException;

    boolean isConnected();
}
