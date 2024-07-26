package com.yofc.iot.component.api;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.eclipse.kura.KuraException;

public interface MqttClientService {

    void publish(String topic, int qos, boolean retain, Object message) throws KuraException;

    void subscribe(String topic, int qos, BiConsumer<String, Object> consumer) throws KuraException;

    void unsubscribe(String topic, BiConsumer<String, Object> consumer) throws KuraException;

    boolean isConnected();

    void connectedLister(Consumer<Void> connectedLister);

    void disConnectedLister(Consumer<Void> disConnectedLister);

    void removeConnectedLister(Consumer<Void> connectedLister);

    void removeDisConnectedLister(Consumer<Void> disConnectedLister);
}
