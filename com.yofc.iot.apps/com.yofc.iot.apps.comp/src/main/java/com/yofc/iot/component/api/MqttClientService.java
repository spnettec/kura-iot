package com.yofc.iot.component.api;

import java.util.List;

import org.eclipse.kura.KuraException;
import org.eclipse.kura.wire.WireRecord;

public interface MqttClientService {

    public Integer publish(String topic, int qos, boolean retain, List<WireRecord> message) throws KuraException;

    public void subscribe(String topic, int qos) throws KuraException;

    public void subscribe(String topic[], int qos[]) throws KuraException;
}
