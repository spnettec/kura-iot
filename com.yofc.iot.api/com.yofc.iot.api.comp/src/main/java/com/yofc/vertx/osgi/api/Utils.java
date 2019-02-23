package com.yofc.vertx.osgi.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.kura.type.TypedValue;
import org.eclipse.kura.type.TypedValues;
import org.eclipse.kura.wire.WireEnvelope;
import org.eclipse.kura.wire.WireRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

public class Utils {

    private static final Logger logger = LoggerFactory.getLogger(Utils.class);

    public static <T> T executeWithTCCLSwitch(Callable<T> action) throws Exception {
        final ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(Utils.class.getClassLoader());
            return action.call();
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    public static void executeWithTCCLSwitch(Runnable action) {
        final ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(Utils.class.getClassLoader());
            action.run();
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    public static <T> T executeWithTCCLSwitch(final ClassLoader classLoader, final Callable<T> action)
            throws Exception {
        if (classLoader == null)
            return action.call();
        final ClassLoader ccl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(classLoader);
        try {
            return action.call();

        } finally {
            Thread.currentThread().setContextClassLoader(ccl);
        }
    }

    public void executeWithTCCLSwitch(final ClassLoader classLoader, Runnable action) {
        if (classLoader == null)
            action.run();
        final ClassLoader ccl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(classLoader);
        try {
            action.run();
        } finally {
            Thread.currentThread().setContextClassLoader(ccl);
        }
    }

    public static WireRecord objectToWireRecord(String tag, Object obj) {
        final Map<String, TypedValue<?>> resultProperties = new HashMap<>();
        final TypedValue<?> resultValue = TypedValues.newTypedValue(obj);
        resultProperties.put(tag, resultValue);
        return new WireRecord(resultProperties);
    }

    public static WireRecord bufferToWireRecord(String tag, Buffer buffer) {
        final Map<String, TypedValue<?>> resultProperties = new HashMap<>();
        final TypedValue<String> resultValue = TypedValues.newStringValue(buffer.toString("UTF-8"));
        resultProperties.put(tag, resultValue);
        return new WireRecord(resultProperties);
    }

    public static List<WireRecord> getWireRecords(WireRecord... records) {
        final List<WireRecord> resultWireRecords = new ArrayList<>();
        for (WireRecord record : records)
            resultWireRecords.add(record);
        return resultWireRecords;
    }

    public static List<WireRecord> bufferToWireRecords(String tag, Buffer buffer) {
        return getWireRecords(bufferToWireRecord(tag, buffer));
    }

    public static TypedValue<?> getValue(String name, List<WireRecord> records) {
        Optional<WireRecord> value = records.stream().filter(record -> {
            return record.getProperties().containsKey(name);
        }).findFirst();
        if (!value.isPresent())
            return null;
        return value.get().getProperties().get(name);
    }

    public static List<WireRecord> filterByTages(List<WireRecord> records, String... tags) {
        if (tags.length == 0)
            return records;
        List<WireRecord> newRecords = new ArrayList<>();
        records.forEach(wireRecord -> newRecords.add(new WireRecord(wireRecord.getProperties().entrySet().stream()
                .filter(value -> Arrays.stream(tags).anyMatch(tag -> value.getKey().equals(tag)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))));
        return newRecords;
    }

    public static WireRecord convertProperies(WireRecord record) {
        Map<String, TypedValue<?>> properties = record.getProperties();
        TypedValue<?> assertName = properties.get("assetName");
        if (assertName == null)
            return record;
        Map<String, TypedValue<?>> map = properties.entrySet().stream().filter(v -> {
            return !v.getKey().equals("assetName");
        }).collect(Collectors.toMap(k -> assertName.getValue().toString() + "." + k.getKey(), Map.Entry::getValue));
        return new WireRecord(map);
    }

    public static boolean isChanged(WireEnvelope preWireEnvelope, WireEnvelope currentWireEnvelope, String tag) {

        return isChanged(preWireEnvelope.getRecords(), currentWireEnvelope.getRecords(), tag);
    }

    public static boolean isOrChanged(WireEnvelope preWireEnvelope, WireEnvelope currentWireEnvelope) {

        return isOrChanged(preWireEnvelope.getRecords(), currentWireEnvelope.getRecords());
    }

    public static boolean isChanged(List<WireRecord> preWireRecords, List<WireRecord> currentRecords, String tag) {

        TypedValue<?> prValue = getValue(tag, preWireRecords);
        if (prValue == null) {
            logger.error("can not find tag:{}", tag);
            return false;
        }
        TypedValue<?> cuValue = getValue(tag, currentRecords);
        if (cuValue == null) {
            logger.error("can not find tag:{}", tag);
            return false;
        }
        return !prValue.equals(cuValue);
    }

    public static boolean isOrChanged(List<WireRecord> preWireRecords, List<WireRecord> currentRecords) {
        return preWireRecords.stream().map(WireRecord::getProperties).flatMap(Stream::of).map(Map::entrySet)
                .flatMap(Set::stream)
                .anyMatch(preV -> currentRecords.stream().map(WireRecord::getProperties).flatMap(Stream::of)
                        .map(Map::entrySet).flatMap(Set::stream).anyMatch(curV -> curV.getKey().equals(preV.getKey())
                                && !curV.getValue().equals(preV.getValue())));
    }

    public static boolean isAndChanged(List<WireRecord> preWireRecords, List<WireRecord> currentRecords) {
        return preWireRecords.stream().map(WireRecord::getProperties).flatMap(Stream::of).map(Map::entrySet)
                .flatMap(Set::stream)
                .allMatch(preV -> currentRecords.stream().map(WireRecord::getProperties).flatMap(Stream::of)
                        .map(Map::entrySet).flatMap(Set::stream).anyMatch(curV -> curV.getKey().equals(preV.getKey())
                                && !curV.getValue().equals(preV.getValue())));
    }

    public static List<String> getTagNames(List<WireRecord> records) {

        return records.stream().map(WireRecord::getProperties).flatMap(Stream::of).map(Map::keySet).flatMap(Set::stream)
                .collect(Collectors.toList());

    }

    public static JsonObject toJson(WireEnvelope wireEnvelope) {
        Stream<Map<String, Object>> properties = wireEnvelope.getRecords().stream()
                .map(record -> record.getProperties().entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getValue())));
        Map<String, Object> merged = properties.map(Map::entrySet).flatMap(Set::stream)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return new JsonObject(merged);

    }

    public static WireRecord toWireRecord(JsonObject object) {
        Map<String, TypedValue<?>> mapWireRecord = object.getMap().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> TypedValues.newTypedValue(entry.getValue())));
        return new WireRecord(mapWireRecord);
    }

    public static List<WireRecord> toWireRecords(JsonObject object) {
        return getWireRecords(toWireRecord(object));
    }

}
