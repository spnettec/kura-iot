package com.yofc.vertx.osgi.api;

import static java.lang.String.format;
import static org.eclipse.kura.configuration.ConfigurationService.KURA_SERVICE_PID;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.kura.db.H2DbService;
import org.eclipse.kura.type.TypedValue;
import org.eclipse.kura.type.TypedValues;
import org.eclipse.kura.wire.WireEnvelope;
import org.eclipse.kura.wire.WireRecord;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import io.netty.util.CharsetUtil;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

public class Utils {

    private Utils() {
        throw new IllegalStateException("Utility class");
    }

    private static final Logger logger = LoggerFactory.getLogger(Utils.class);

    private static final BundleContext bundleContext = FrameworkUtil.getBundle(Utils.class).getBundleContext();

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
        if (classLoader == null) {
            return action.call();
        }
        final ClassLoader ccl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(classLoader);
        try {
            return action.call();

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
        Collections.addAll(resultWireRecords, records);
        return resultWireRecords;
    }

    public static List<WireRecord> bufferToWireRecords(String tag, Buffer buffer) {
        return getWireRecords(bufferToWireRecord(tag, buffer));
    }

    public static TypedValue<?> getValue(String name, List<WireRecord> records) {
        Optional<WireRecord> value = records.stream().filter(record -> record.getProperties().containsKey(name))
                .findFirst();
        return value.<TypedValue<?>> map(wireRecord -> wireRecord.getProperties().get(name)).orElse(null);
    }

    public static List<WireRecord> filterByTages(List<WireRecord> records, String... tags) {
        if (tags.length == 0) {
            return records;
        }
        List<WireRecord> newRecords = new ArrayList<>();
        records.forEach(wireRecord -> newRecords.add(new WireRecord(wireRecord.getProperties().entrySet().stream()
                .filter(value -> Arrays.stream(tags).anyMatch(tag -> value.getKey().equals(tag)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))));
        return newRecords;
    }

    public static WireRecord convertProperies(WireRecord record) {
        Map<String, TypedValue<?>> properties = record.getProperties();
        TypedValue<?> assertName = properties.get("assetName");
        if (assertName == null) {
            return record;
        }
        Map<String, TypedValue<?>> map = properties.entrySet().stream().filter(v -> !"assetName".equals(v.getKey()))
                .collect(Collectors.toMap(k -> assertName.getValue().toString() + "." + k.getKey(),
                        Map.Entry::getValue));
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

        return records.stream().map(WireRecord::getProperties).flatMap(Stream::of).map(functionWrapper(Map::keySet))
                .flatMap(functionWrapper(Set::stream)).toList();
    }

    public static Map<String, Object> toMap(List<WireRecord> list) {
        Stream<Map<String, Object>> properties = list.stream()
                .map(wireRecord -> wireRecord.getProperties().entrySet().stream().collect(Collectors.toMap(
                        functionWrapper(Map.Entry::getKey), functionWrapper(entry -> entry.getValue().getValue()))));
        return properties.map(Map::entrySet).flatMap(Set::stream)
                .collect(Collectors.toMap(functionWrapper(Map.Entry::getKey), functionWrapper(Map.Entry::getValue)));
    }

    public static JsonObject toJson(List<WireRecord> list) {
        return new JsonObject(toMap(list));
    }

    public static JsonObject toJson(WireEnvelope wireEnvelope) {
        return toJson(wireEnvelope.getRecords());
    }

    public static WireRecord toWireRecord(Map<String, Object> map) {
        Map<String, TypedValue<?>> mapWireRecord = map.entrySet().stream()
                .collect(Collectors.toMap(functionWrapper(Map.Entry::getKey),
                        functionWrapper(entry -> TypedValues.newTypedValue(entry.getValue()))));
        return new WireRecord(mapWireRecord);
    }

    public static WireRecord toWireRecord(JsonObject object) {
        return toWireRecord(object.getMap());
    }

    public static List<WireRecord> toWireRecords(JsonObject object) {
        return getWireRecords(toWireRecord(object));
    }

    public static List<WireRecord> toWireRecords(Map<String, Object> object) {
        return getWireRecords(toWireRecord(object));
    }

    public static String utf8ToAscii(String utf8String) {
        return new String(utf8String.getBytes(CharsetUtil.UTF_8), CharsetUtil.US_ASCII);
    }

    public static Object excuteQuerySQL(String pid, String sql) throws InvalidSyntaxException, SQLException {
        final String filter = format("(%s=%s)", KURA_SERVICE_PID, pid);
        Collection<ServiceReference<H2DbService>> refs = bundleContext.getServiceReferences(H2DbService.class, filter);
        if (refs.isEmpty()) {
            throw new InvalidSyntaxException("can not find H2DbService instance", filter);
        }
        if (refs.size() > 1) {
            for (ServiceReference<H2DbService> unref : refs) {
                bundleContext.ungetService(unref);
            }
            throw new InvalidSyntaxException("find mutipile H2DbService instance", filter);
        }
        ServiceReference<H2DbService> ref = refs.iterator().next();
        try {
            H2DbService h2DbService = bundleContext.getService(ref);
            return h2DbService.withConnection(c -> {
                try (final Statement stmt = c.createStatement(); final ResultSet resultSet = stmt.executeQuery(sql)) {
                    JSONArray jsonArray = new JSONArray();
                    ResultSetMetaData metaData = resultSet.getMetaData();
                    int totalRows = metaData.getColumnCount();
                    while (resultSet.next()) {
                        JSONObject obj = new JSONObject();
                        for (int i = 0; i < totalRows; i++) {
                            obj.put(metaData.getColumnLabel(i + 1).toLowerCase(), resultSet.getObject(i + 1));
                        }
                        jsonArray.add(obj);
                    }
                    return jsonArray;
                } catch (Exception e) {
                    logger.error("sql error", e);
                }
                return null;
            });
        } finally {
            bundleContext.ungetService(ref);
        }
    }

    public static void excuteSQL(String pid, String sql) throws InvalidSyntaxException, SQLException {
        final String filter = format("(%s=%s)", KURA_SERVICE_PID, pid);
        Collection<ServiceReference<H2DbService>> refs = bundleContext.getServiceReferences(H2DbService.class, filter);
        if (refs.isEmpty()) {
            throw new InvalidSyntaxException("can not find H2DbService instance", filter);
        }
        if (refs.size() > 1) {
            for (ServiceReference<H2DbService> unref : refs) {
                bundleContext.ungetService(unref);
            }
            throw new InvalidSyntaxException("find mutipile H2DbService instance", filter);
        }
        ServiceReference<H2DbService> ref = refs.iterator().next();
        try {
            H2DbService h2DbService = bundleContext.getService(ref);
            h2DbService.withConnection(c -> {
                try (final PreparedStatement stmt = c.prepareStatement(sql)) {
                    stmt.execute();
                    c.commit();
                    return null;
                }
            });
        } finally {
            bundleContext.ungetService(ref);
        }
    }

    public static <T, R, E extends Exception> Function<T, R> functionWrapper(FunctionWithException<T, R, E> fe) {
        return arg -> {
            try {
                return fe.apply(arg);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    public static <T> Consumer<T> consumerWrapper(ConsumerWithException<T, Exception> throwingConsumer) {
        return i -> {
            try {
                throwingConsumer.accept(i);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        };
    }

    public static <T, U> BiConsumer<T, U> BiConsumerWrapper(BiConsumerWithException<T, U, Exception> throwingConsumer) {
        return (i, u) -> {
            try {
                throwingConsumer.accept(i, u);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        };
    }

    public static int saturatedCast(long value) {
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (value < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) value;
    }

    public static void executeWithTCCLSwitch(final ClassLoader classLoader, Runnable action) {
        if (classLoader == null) {
            action.run();
        }
        final ClassLoader ccl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(classLoader);
        try {
            action.run();
        } finally {
            Thread.currentThread().setContextClassLoader(ccl);
        }
    }

    public static <T> T tryExtract(final Map<String, Object> properties, final Function<Map<String, Object>, T> func,
            final String errorMessage) {
        try {
            return func.apply(properties);
        } catch (Exception e) {
            throw new IllegalArgumentException(errorMessage);
        }
    }
}
