package com.yofc.plc4x.driver;

import static java.util.Objects.requireNonNull;

import java.lang.reflect.Array;
import java.math.BigInteger;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.PlcConnectionManager;
import org.apache.plc4x.java.api.messages.PlcReadRequest;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.apache.plc4x.java.api.messages.PlcSubscriptionEvent;
import org.apache.plc4x.java.api.messages.PlcSubscriptionRequest;
import org.apache.plc4x.java.api.messages.PlcSubscriptionResponse;
import org.apache.plc4x.java.api.messages.PlcWriteRequest;
import org.apache.plc4x.java.api.messages.PlcWriteResponse;
import org.apache.plc4x.java.api.model.PlcSubscriptionHandle;
import org.apache.plc4x.java.api.types.PlcResponseCode;
import org.apache.plc4x.java.api.value.PlcValue;
import org.eclipse.kura.channel.ChannelFlag;
import org.eclipse.kura.channel.ChannelRecord;
import org.eclipse.kura.channel.ChannelStatus;
import org.eclipse.kura.channel.listener.ChannelEvent;
import org.eclipse.kura.channel.listener.ChannelListener;
import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.driver.ChannelDescriptor;
import org.eclipse.kura.driver.Driver;
import org.eclipse.kura.driver.PreparedRead;
import org.eclipse.kura.type.DataType;
import org.eclipse.kura.type.TypedValue;
import org.eclipse.kura.type.TypedValues;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yofc.plc4x.driver.Plc4xDriver.Config;

@Designate(ocd = Config.class, factory = true)
@Component(immediate = true, enabled = true, configurationPolicy = ConfigurationPolicy.REQUIRE)
public class Plc4xDriver implements Driver, ConfigurableComponent {

    private static final Logger logger = LoggerFactory.getLogger(Plc4xDriver.class);
    private static final String REQUEST_TIMEOUT_PROPERTY = "request.timeout";
    private static final long DEFAULT_REQUEST_TIMEOUT_SECONDS = 10L;
    private static final long READ_STATS_LOG_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(10);
    private final Map<ChannelListener, List<PlcSubscriptionHandle>> registeredListeners = Collections
            .synchronizedMap(new HashMap<>());

    private final AtomicReference<Config> options = new AtomicReference<>();
    private final AtomicLong readRequestCount = new AtomicLong();
    private final AtomicLong readStatsLastLogNanos = new AtomicLong();
    private final AtomicReference<Boolean> readFailing = new AtomicReference<>(Boolean.FALSE);
    private final ReentrantLock readLock = new ReentrantLock();
    private final Map<String, TypedValue<?>> lastSuccessfulReadValues = new ConcurrentHashMap<>();

    @Reference
    private PlcConnectionManager plcConnectionManager;

    @Activate
    protected synchronized void activate(final BundleContext context, final Config properties) {
        logger.info("Activating Plc4xDriver...");

        extractProperties(properties);
        logger.info("Activating Plc4xDriver... Done");
    }

    private void extractProperties(final Config properties) {
        requireNonNull(properties, "Properties cannot be null");
        this.options.set(properties);
    }

    @Modified
    public synchronized void updated(final Config properties) {
        logger.info("Updating Plc4xDriver ...");

        // If the connection string changed, release the cached connection bound to the old URL —
        // otherwise its serial fd / TCP socket leaks until the bundle restarts. Same-URL updates
        // (only baud rate / unit identifier changed in query? no — query is part of connectString)
        // safely call this too: invalidate is a no-op when the URL isn't cached, and changing any
        // query parameter is also a semantic change that warrants dropping the old container.
        final Config previous = this.options.get();
        final String previousUrl = (previous == null) ? null : previous.connectString();
        extractProperties(properties);
        final String currentUrl = properties.connectString();
        if (previousUrl != null && !previousUrl.equals(currentUrl)) {
            invalidateCachedConnection(previousUrl);
            lastSuccessfulReadValues.clear();
        }

        logger.info("Updating Plc4xDriver Driver... Done");
    }

    @Deactivate
    public synchronized void deactivate() {
        logger.info("Deactivating Plc4xDriver: active listeners={} ...", registeredListeners.size());

        // Release all subscription handles first — each underlying OPC UA subscription
        // holds a scheduled publishTask that must be cancelled, otherwise it keeps the
        // static EXECUTOR thread alive and accumulates stale tasks.
        unregisterAll();

        // Release the cached connection so the underlying fd is closed when this driver instance
        // goes away. Without this the container would linger until com.yofc.iot.apps.opcuaserver
        // itself stops.
        final Config current = this.options.get();
        if (current != null) {
            invalidateCachedConnection(current.connectString());
        }
        lastSuccessfulReadValues.clear();

        logger.info("Deactivating Plc4xDriver... Done");
    }

    /**
     * Cancel all active subscriptions and clear the listener registry without requiring
     * a live PLC connection. Called by {@link #deactivate()} and also safe to invoke
     * when the PLC is already unreachable (best-effort).
     */
    private void unregisterAll() {
        if (registeredListeners.isEmpty()) {
            return;
        }
        logger.info("Unregistering all {} channel listener(s)...", registeredListeners.size());

        // Collect all handles across every listener.
        final List<PlcSubscriptionHandle> allHandles = new ArrayList<>();
        synchronized (registeredListeners) {
            for (List<PlcSubscriptionHandle> handles : registeredListeners.values()) {
                allHandles.addAll(handles);
            }
        }

        // Best-effort: tell the PLC we're done. If the connection is gone this will fail,
        // but we still clear our local state below — no leak on deactivate.
        if (!allHandles.isEmpty()) {
            try (PlcConnection plcConnection = plcConnectionManager.getConnection(this.options.get().connectString())) {
                plcConnection.unsubscriptionRequestBuilder().addHandles(allHandles).build().execute().get(5,
                        TimeUnit.SECONDS);
                logger.info("Successfully unsubscribed {} handle(s)", allHandles.size());
            } catch (Exception e) {
                logger.warn("Could not send unsubscribe to PLC (connection likely gone): {}", e.getMessage());
                // Continue — we still clear registeredListeners below.
            }
        }

        registeredListeners.clear();
        logger.info("All listener registrations cleared.");
    }

    private void invalidateCachedConnection(String url) {
        if (url == null) {
            return;
        }
        try {
            plcConnectionManager.invalidate(url);
        } catch (Exception e) {
            logger.warn("Failed to invalidate cached connection for {}", url, e);
        }
    }

    @Override
    public void connect() {

    }

    @Override
    public void disconnect() {

    }

    @Override
    public ChannelDescriptor getChannelDescriptor() {
        return new Plc4xChannelDescriptor();
    }

    @Override
    public void read(List<ChannelRecord> records) throws ConnectionException {
        Map<String, TagRecord> tagRecords = getTagRecords(records);
        if (tagRecords.isEmpty()) {
            return;
        }
        executeRead(records, tagRecords);
    }

    private void executeRead(List<ChannelRecord> records, Map<String, TagRecord> tagRecords)
            throws ConnectionException {
        final long startNanos = System.nanoTime();
        int failureCount = 0;
        String firstFailure = null;
        boolean connectionFailure = false;
        boolean locked = readLock.tryLock();

        if (!locked && applyCachedReadValues(records)) {
            logReadStats(tagRecords.size(), records.size(), 0,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos), null);
            return;
        }

        if (!locked) {
            readLock.lock();
            locked = true;
            if (applyCachedReadValues(records)) {
                readLock.unlock();
                locked = false;
                logReadStats(tagRecords.size(), records.size(), 0,
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos), null);
                return;
            }
        }

        try (PlcConnection plcConnection = plcConnectionManager.getConnection(this.options.get().connectString())) {
            PlcReadRequest.Builder builder = plcConnection.readRequestBuilder();
            tagRecords.forEach((tagName, tagRecord) -> builder.addTagAddress(tagName, tagRecord.tagAddr));
            PlcReadRequest readRequest = builder.build();
            PlcReadResponse response = readRequest.execute().get(getRequestTimeoutSeconds(records), TimeUnit.SECONDS);
            for (String field : response.getTagNames()) {
                TagRecord tagRecord = tagRecords.get(field);
                if (tagRecord == null) {
                    logger.warn("PLC read returned unexpected tag {}", field);
                    continue;
                }

                ChannelRecord channelRecord = tagRecord.channelRecord;
                channelRecord.setTimestamp(System.currentTimeMillis());
                PlcResponseCode responseCode = response.getResponseCode(field);
                if (responseCode != PlcResponseCode.OK) {
                    channelRecord.setChannelStatus(new ChannelStatus(ChannelFlag.FAILURE, responseCode.name(), null));
                    failureCount++;
                    if (firstFailure == null) {
                        firstFailure = field + "=" + responseCode.name();
                    }
                    continue;
                }
                try {
                    TypedValue<?> mappedValue = map(response.getObject(field), channelRecord.getValueType());
                    channelRecord.setValue(mappedValue);
                    channelRecord.setChannelStatus(new ChannelStatus(ChannelFlag.SUCCESS));
                    lastSuccessfulReadValues.put(channelRecord.getChannelName(), mappedValue);
                } catch (Exception e) {
                    channelRecord.setChannelStatus(new ChannelStatus(ChannelFlag.FAILURE, e.getMessage(), e));
                    failureCount++;
                    if (firstFailure == null) {
                        firstFailure = field + "=" + e.getMessage();
                    }
                }
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            failureCount = records.size();
            firstFailure = e.getMessage();
            connectionFailure = true;
            throw new ConnectionException(e);
        } finally {
            if (connectionFailure) {
                invalidateCachedConnection(this.options.get().connectString());
            }
            if (locked) {
                readLock.unlock();
            }
            logReadStats(tagRecords.size(), records.size(), failureCount,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos), firstFailure);
        }

    }

    private boolean applyCachedReadValues(List<ChannelRecord> records) {
        for (ChannelRecord record : records) {
            if (!lastSuccessfulReadValues.containsKey(record.getChannelName())) {
                return false;
            }
        }

        long timestamp = System.currentTimeMillis();
        for (ChannelRecord record : records) {
            record.setValue(lastSuccessfulReadValues.get(record.getChannelName()));
            record.setTimestamp(timestamp);
            record.setChannelStatus(new ChannelStatus(ChannelFlag.SUCCESS));
        }
        return true;
    }

    private void logReadStats(int tagCount, int recordCount, int failureCount, long durationMs, String firstFailure) {
        final long requestCount = readRequestCount.incrementAndGet();
        final long now = System.nanoTime();
        final long last = readStatsLastLogNanos.get();
        final boolean failed = failureCount > 0;
        final boolean transitioned = readFailing.getAndSet(failed) != failed;

        if (requestCount == 1 || transitioned) {
            readStatsLastLogNanos.set(now);
            logger.debug(
                    "PLC read completed: requestCount={}, tags={}, records={}, failures={}, durationMs={}, firstFailure={}",
                    requestCount, tagCount, recordCount, failureCount, durationMs,
                    firstFailure == null ? "" : firstFailure);
        } else if (now - last >= READ_STATS_LOG_INTERVAL_NANOS && readStatsLastLogNanos.compareAndSet(last, now)) {
            logger.debug(
                    "PLC read completed: requestCount={}, tags={}, records={}, failures={}, durationMs={}, firstFailure={}",
                    requestCount, tagCount, recordCount, failureCount, durationMs,
                    firstFailure == null ? "" : firstFailure);
        }
    }

    @Override
    public void write(List<ChannelRecord> records) {
        try (PlcConnection plcConnection = plcConnectionManager.getConnection(this.options.get().connectString())) {

            Map<String, TagRecord> tagRecords = getTagRecords(records);
            if (tagRecords.isEmpty()) {
                return;
            }
            PlcWriteRequest.Builder builder = plcConnection.writeRequestBuilder();
            Iterator<Entry<String, TagRecord>> iterator = tagRecords.entrySet().iterator();
            while (iterator.hasNext()) {
                Entry<String, TagRecord> entry = iterator.next();
                String tagName = entry.getKey();
                TagRecord tagRecord = entry.getValue();
                ChannelRecord channelRecord = tagRecord.channelRecord;
                if (channelRecord.getValue() == null) {
                    failRecord(channelRecord, "Channel value cannot be null", null);
                    iterator.remove();
                    continue;
                }
                builder.addTagAddress(tagName, tagRecord.tagAddr, channelRecord.getValue().getValue());
            }
            if (tagRecords.isEmpty()) {
                return;
            }
            PlcWriteRequest writeRequest = builder.build();

            PlcWriteResponse response = writeRequest.execute().get(getRequestTimeoutSeconds(records), TimeUnit.SECONDS);
            for (String field : response.getTagNames()) {
                TagRecord tagRecord = tagRecords.get(field);
                if (tagRecord == null) {
                    logger.warn("PLC write returned unexpected tag {}", field);
                    continue;
                }

                ChannelRecord channelRecord = tagRecord.channelRecord;
                channelRecord.setTimestamp(System.currentTimeMillis());
                PlcResponseCode responseCode = response.getResponseCode(field);
                if (responseCode == PlcResponseCode.OK) {
                    channelRecord.setChannelStatus(new ChannelStatus(ChannelFlag.SUCCESS));
                } else {
                    channelRecord.setChannelStatus(new ChannelStatus(ChannelFlag.FAILURE, responseCode.name(), null));
                }
            }
        } catch (Exception e) {
            records.forEach(channelRecord -> {
                channelRecord.setChannelStatus(new ChannelStatus(ChannelFlag.FAILURE, e.getMessage(), e));
                channelRecord.setTimestamp(System.currentTimeMillis());
            });
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void registerChannelListeners(final Map<ChannelListener, Map<String, Object>> listenerChannelConfigs)
            throws ConnectionException {

        if (listenerChannelConfigs.isEmpty()) {
            return;
        }

        // ---- Idempotency: clean up handles that already exist for these listeners ----
        final List<ChannelListener> existingListeners = listenerChannelConfigs.keySet().stream()
                .filter(registeredListeners::containsKey).toList();
        if (!existingListeners.isEmpty()) {
            logger.info("Re-registering {} existing listener(s): unsubscribing old handles first",
                    existingListeners.size());
            unregisterSilently(existingListeners);
        }

        // ---- Build a tag-name → (listener, config) lookup for the handler ----
        final Map<String, ListenerConfig> tagToConfig = new LinkedHashMap<>();
        for (Entry<ChannelListener, Map<String, Object>> entry : listenerChannelConfigs.entrySet()) {
            final ChannelListener listener = entry.getKey();
            final Map<String, Object> channelConfig = entry.getValue();
            final String channelName = getRequiredConfigProperty(channelConfig, "+name");
            final String tagAddr = getRequiredConfigProperty(channelConfig, Plc4xChannelDescriptor.DATA_TAG_ADDR_ID);
            final long samplingInterval = Long.parseLong(getRequiredConfigProperty(channelConfig,
                    Plc4xChannelDescriptor.LISTEN_SAMPLING_INTERVAL_PROP_NAME));
            tagToConfig.put(channelName, new ListenerConfig(listener, channelConfig, tagAddr, samplingInterval));
        }

        // ---- Create PLC subscription ----
        final PlcSubscriptionResponse subscriptionResponse;
        try (PlcConnection plcConnection = plcConnectionManager.getConnection(this.options.get().connectString())) {
            final PlcSubscriptionRequest.Builder sbuilder = plcConnection.subscriptionRequestBuilder();
            for (Entry<String, ListenerConfig> tagEntry : tagToConfig.entrySet()) {
                final ListenerConfig lc = tagEntry.getValue();
                sbuilder.addChangeOfStateTagAddress(tagEntry.getKey(), lc.tagAddr,
                        Duration.ofMillis(lc.samplingInterval));
            }
            subscriptionResponse = sbuilder.build().execute().get();
            logger.info("Subscription created: {} tag(s)", subscriptionResponse.getTagNames().size());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConnectionException(e);
        } catch (Exception e) {
            throw new ConnectionException(e);
        }

        // ---- Bind each returned handle to its listener via a lightweight handler ----
        int registeredCount = 0;
        for (String tagName : subscriptionResponse.getTagNames()) {
            final ListenerConfig lc = tagToConfig.get(tagName);
            if (lc == null) {
                logger.warn("PLC subscription returned unexpected tag '{}'", tagName);
                continue;
            }
            final PlcSubscriptionHandle handle = subscriptionResponse.getSubscriptionHandle(tagName);
            handle.register(new ValueChangeHandler(lc.listener, lc.channelConfig));
            registeredListeners.computeIfAbsent(lc.listener, k -> new ArrayList<>()).add(handle);
            registeredCount++;
        }
        logger.info("Registered {} subscription handle(s) across {} listener(s)", registeredCount,
                listenerChannelConfigs.size());
    }

    /**
     * Per-listener config snapshot, built during registration so that the
     * {@link ValueChangeHandler} only holds the exact listener + config it needs.
     */
    private static final class ListenerConfig {

        final ChannelListener listener;
        final Map<String, Object> channelConfig;
        final String tagAddr;
        final long samplingInterval;

        ListenerConfig(ChannelListener listener, Map<String, Object> channelConfig, String tagAddr,
                long samplingInterval) {
            this.listener = listener;
            this.channelConfig = channelConfig;
            this.tagAddr = tagAddr;
            this.samplingInterval = samplingInterval;
        }
    }

    /**
     * Event handler that receives PLC subscription events and forwards them to a
     * single {@link ChannelListener}. It only holds the specific config for the
     * listener it serves — not the full register-time {@code listenerChannelConfigs} map.
     */
    private static class ValueChangeHandler implements Consumer<PlcSubscriptionEvent> {

        private final ChannelListener channelListener;
        private final Map<String, Object> channelConfig;

        ValueChangeHandler(ChannelListener channelListener, Map<String, Object> channelConfig) {
            this.channelListener = channelListener;
            this.channelConfig = channelConfig;
        }

        @Override
        public void accept(PlcSubscriptionEvent plcSubscriptionEvent) {
            for (String fieldName : plcSubscriptionEvent.getTagNames()) {
                final PlcValue plcValue = plcSubscriptionEvent.getPlcValue(fieldName);
                if (plcValue == null) {
                    logger.warn("Null value for tag '{}' in subscription event", fieldName);
                    continue;
                }
                if (plcValue.isStruct()) {
                    logger.warn("Struct value not supported for tag '{}': {}", fieldName, plcValue);
                    continue;
                }
                try {
                    final DataType valueType = DataType
                            .valueOf(getRequiredConfigProperty(channelConfig, "+value.type"));
                    final ChannelRecord channelRecord = ChannelRecord.createReadRecord(fieldName, valueType);
                    channelRecord.setTimestamp(System.currentTimeMillis());
                    channelRecord.setValue(map(plcValue.getObject(), valueType));
                    channelRecord.setChannelStatus(new ChannelStatus(ChannelFlag.SUCCESS));
                    channelListener.onChannelEvent(new ChannelEvent(channelRecord));
                } catch (Exception e) {
                    logger.warn("Failed to handle subscription event for tag '{}': {}", fieldName, e.getMessage());
                }
            }
        }
    }

    @Override
    public void unregisterChannelListeners(final Collection<ChannelListener> listeners) throws ConnectionException {
        if (registeredListeners.isEmpty() || listeners.isEmpty()) {
            return;
        }
        unregisterSilently(listeners);
    }

    /**
     * Unsubscribe the given listeners' handles from the PLC (best-effort) and
     * unconditionally remove them from the local registry afterwards.
     * <p>
     * This is safe to call even when the PLC is unreachable — the local cleanup
     * always happens so no stale handle references survive on the Java side.
     */
    private synchronized void unregisterSilently(final Collection<ChannelListener> listeners) {
        final List<PlcSubscriptionHandle> handles = listeners.stream().map(registeredListeners::get)
                .filter(Objects::nonNull).flatMap(Collection::stream).toList();

        if (!handles.isEmpty()) {
            try (PlcConnection plcConnection = plcConnectionManager.getConnection(this.options.get().connectString())) {
                plcConnection.unsubscriptionRequestBuilder().addHandles(handles).build().execute().get(5,
                        TimeUnit.SECONDS);
                logger.info("Unsubscribed {} handle(s)", handles.size());
            } catch (Exception e) {
                logger.warn("Could not send unsubscribe to PLC (connection may be gone): {}", e.getMessage());
                // Continue — still remove from local map below.
            }
        }

        listeners.forEach(registeredListeners::remove);
    }

    private Map<String, TagRecord> getTagRecords(List<ChannelRecord> records) {
        Map<String, TagRecord> result = new LinkedHashMap<>();
        records.forEach(channelRecord -> {
            try {
                String tagName = getRequiredChannelProperty(channelRecord, "+name");
                String tagAddr = getRequiredChannelProperty(channelRecord, Plc4xChannelDescriptor.DATA_TAG_ADDR_ID);
                if (result.containsKey(tagName)) {
                    failRecord(channelRecord, "Duplicate PLC tag name: " + tagName, null);
                    return;
                }
                result.put(tagName, new TagRecord(channelRecord, tagAddr));
            } catch (Exception e) {
                failRecord(channelRecord, e.getMessage(), e);
            }
        });
        return result;
    }

    private static String getRequiredChannelProperty(ChannelRecord channelRecord, String propertyName) {
        return getRequiredConfigProperty(channelRecord.getChannelConfig(), propertyName);
    }

    private static String getRequiredConfigProperty(Map<String, Object> config, String propertyName) {
        Object value = config.get(propertyName);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("Missing channel property: " + propertyName);
        }
        return value.toString();
    }

    private static void failRecord(ChannelRecord channelRecord, String message, Exception cause) {
        channelRecord.setChannelStatus(new ChannelStatus(ChannelFlag.FAILURE, message, cause));
        channelRecord.setTimestamp(System.currentTimeMillis());
    }

    private static long getRequestTimeoutSeconds(List<ChannelRecord> records) {
        return records.stream().map(ChannelRecord::getChannelConfig).filter(Objects::nonNull)
                .map(config -> config.get(REQUEST_TIMEOUT_PROPERTY)).filter(Objects::nonNull).findFirst()
                .map(Plc4xDriver::parseRequestTimeoutSeconds).orElse(DEFAULT_REQUEST_TIMEOUT_SECONDS);
    }

    private static long parseRequestTimeoutSeconds(Object value) {
        try {
            long timeout = value instanceof Number number ? number.longValue() : Long.parseLong(value.toString());
            return timeout > 0 ? timeout : DEFAULT_REQUEST_TIMEOUT_SECONDS;
        } catch (Exception e) {
            logger.warn("Invalid {} value '{}', using default {} seconds", REQUEST_TIMEOUT_PROPERTY, value,
                    DEFAULT_REQUEST_TIMEOUT_SECONDS);
            return DEFAULT_REQUEST_TIMEOUT_SECONDS;
        }
    }

    @Override
    public PreparedRead prepareRead(List<ChannelRecord> records) {
        requireNonNull(records, "records cannot be null");
        Map<String, TagRecord> tagRecords = getTagRecords(records);
        return new Plc4xPreparedRead(records, tagRecords);
    }

    private final class Plc4xPreparedRead implements PreparedRead {

        private final List<ChannelRecord> records;
        private final Map<String, TagRecord> tagRecords;
        private boolean closed;

        private Plc4xPreparedRead(List<ChannelRecord> records, Map<String, TagRecord> tagRecords) {
            this.records = records;
            this.tagRecords = tagRecords;
        }

        @Override
        public List<ChannelRecord> execute() throws ConnectionException {
            if (closed) {
                throw new ConnectionException("Prepared read is closed");
            }
            if (!tagRecords.isEmpty()) {
                executeRead(records, tagRecords);
            }
            return records;
        }

        @Override
        public List<ChannelRecord> getChannelRecords() {
            return records;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static TypedValue<?> map(final Object value, final DataType targetType) throws Exception {
        switch (targetType) {
        case LONGS:
            return TypedValues.newLongValues(getLongs(value));
        case LONG: {
            return TypedValues.newLongValue(toLong(value));
        }
        case FLOATS:
            return TypedValues.newFloatValues(getFloats(value));
        case FLOAT:
            return TypedValues.newFloatValue(toFloat(value));
        case DOUBLES:
            return TypedValues.newDoubleValues(getDoubles(value));
        case DOUBLE:
            return TypedValues.newDoubleValue(toDouble(value));
        case INTEGERS:
            return TypedValues.newIntegerValues(getIntegers(value));
        case BIGINTEGERS:
            return TypedValues.newBigIntegerValues(getBigIntegers(value));
        case BOOLEANS:
            return TypedValues.newBooleanValues(getBooleans(value));
        case INTEGER:
            return TypedValues.newIntegerValue(toInteger(value));
        case BOOLEAN: {
            return TypedValues.newBooleanValue(toBoolean(value));
        }
        case STRINGS:
            return TypedValues.newStringValues(getStrings(value));
        case STRING:
            if (isMultiValue(value)) {
                return TypedValues.newStringValue(ArrayUtils.toString(getStrings(value)));
            }
            return TypedValues.newStringValue(toStringValue(value));
        case BYTE_ARRAY:
            if (value == null) {
                return TypedValues.newByteArrayValue(new byte[] {});
            }
            if (value instanceof byte[] bytes) {
                return TypedValues.newByteArrayValue(bytes);
            }
            Byte[] bytes = asList(value).stream().map(Plc4xDriver::toByte).toArray(Byte[]::new);
            return TypedValues.newByteArrayValue(ArrayUtils.toPrimitive(bytes));
        default:
            throw new IllegalArgumentException();
        }
    }

    private static Float[] getFloats(Object value) {
        return asList(value).stream().map(Plc4xDriver::toFloat).toArray(Float[]::new);
    }

    private static Boolean[] getBooleans(Object value) {
        return asList(value).stream().map(Plc4xDriver::toBoolean).toArray(Boolean[]::new);
    }

    private static Double[] getDoubles(Object value) {
        return asList(value).stream().map(Plc4xDriver::toDouble).toArray(Double[]::new);
    }

    private static Integer[] getIntegers(Object value) {
        return asList(value).stream().map(Plc4xDriver::toInteger).toArray(Integer[]::new);
    }

    private static Long[] getLongs(Object value) {
        return asList(value).stream().map(Plc4xDriver::toLong).toArray(Long[]::new);
    }

    private static BigInteger[] getBigIntegers(Object value) {
        return asList(value).stream().map(Plc4xDriver::toBigInteger).toArray(BigInteger[]::new);
    }

    private static String[] getStrings(Object value) {
        return asList(value).stream().map(Plc4xDriver::toStringValue).toArray(String[]::new);
    }

    private static boolean isMultiValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof PlcValue plcValue) {
            return plcValue.isList();
        }
        return value instanceof List<?> || value.getClass().isArray();
    }

    private static List<?> asList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof PlcValue plcValue) {
            if (plcValue.isNull()) {
                return List.of();
            }
            if (plcValue.isList()) {
                return plcValue.getList();
            }
            return List.of(plcValue);
        }
        if (value instanceof List<?> list) {
            return list;
        }
        Class<?> valueClass = value.getClass();
        if (valueClass.isArray()) {
            return IntStream.range(0, Array.getLength(value)).mapToObj(i -> Array.get(value, i)).toList();
        }
        return List.of(value);
    }

    private static Object unwrap(Object value) {
        if (value instanceof PlcValue plcValue) {
            if (plcValue.isNull()) {
                return null;
            }
            if (plcValue.isDate()) {
                return plcValue.getDate();
            }
            if (plcValue.isTime()) {
                return plcValue.getTime();
            }
            if (plcValue.isDateTime()) {
                return plcValue.getDateTime();
            }
            if (plcValue.isDuration()) {
                return plcValue.getDuration();
            }
            return plcValue.getObject();
        }
        return value;
    }

    private static float toFloat(Object value) {
        Object unwrapped = unwrap(value);
        if (unwrapped == null) {
            return 0;
        }
        if (unwrapped instanceof Number number) {
            return number.floatValue();
        }
        if (unwrapped instanceof Boolean bool) {
            return bool ? 1 : 0;
        }
        return Float.parseFloat(unwrapped.toString());
    }

    private static double toDouble(Object value) {
        Object unwrapped = unwrap(value);
        if (unwrapped == null) {
            return 0;
        }
        if (unwrapped instanceof Number number) {
            return number.doubleValue();
        }
        if (unwrapped instanceof Boolean bool) {
            return bool ? 1 : 0;
        }
        return Double.parseDouble(unwrapped.toString());
    }

    private static int toInteger(Object value) {
        Object unwrapped = unwrap(value);
        if (unwrapped == null) {
            return 0;
        }
        if (unwrapped instanceof Number number) {
            return number.intValue();
        }
        if (unwrapped instanceof Boolean bool) {
            return bool ? 1 : 0;
        }
        return Integer.parseInt(unwrapped.toString());
    }

    private static long toLong(Object value) {
        Object unwrapped = unwrap(value);
        if (unwrapped == null) {
            return 0;
        }
        if (unwrapped instanceof LocalDate localDate) {
            return localDate.toEpochDay();
        }
        if (unwrapped instanceof LocalTime localTime) {
            return localTime.toNanoOfDay();
        }
        if (unwrapped instanceof LocalDateTime localDateTime) {
            return localDateTime.toInstant(ZoneOffset.UTC).toEpochMilli();
        }
        if (unwrapped instanceof Duration duration) {
            return duration.toNanos();
        }
        if (unwrapped instanceof Date date) {
            return date.getTime();
        }
        if (unwrapped instanceof Number number) {
            return number.longValue();
        }
        if (unwrapped instanceof Boolean bool) {
            return bool ? 1 : 0;
        }
        return Long.parseLong(unwrapped.toString());
    }

    private static BigInteger toBigInteger(Object value) {
        Object unwrapped = unwrap(value);
        if (unwrapped == null) {
            return BigInteger.ZERO;
        }
        if (unwrapped instanceof BigInteger bigInteger) {
            return bigInteger;
        }
        if (unwrapped instanceof Number number) {
            return BigInteger.valueOf(number.longValue());
        }
        if (unwrapped instanceof Boolean bool) {
            return bool ? BigInteger.ONE : BigInteger.ZERO;
        }
        return new BigInteger(unwrapped.toString());
    }

    private static boolean toBoolean(Object value) {
        Object unwrapped = unwrap(value);
        if (unwrapped == null) {
            return false;
        }
        if (unwrapped instanceof Boolean bool) {
            return bool;
        }
        if (unwrapped instanceof Number number) {
            return number.intValue() > 0;
        }
        return Boolean.parseBoolean(unwrapped.toString());
    }

    private static String toStringValue(Object value) {
        Object unwrapped = unwrap(value);
        return unwrapped == null ? "" : unwrapped.toString();
    }

    private static byte toByte(Object value) {
        if (value instanceof PlcValue plcValue && plcValue.isList()) {
            byte receivedId = 0;
            for (int i = 0; i < Math.min(8, plcValue.getLength()); i++) {
                receivedId |= (byte) (toInteger(plcValue.getIndex(i)) << i);
            }
            return receivedId;
        }
        return (byte) toInteger(value);
    }

    static final class TagRecord {

        private final ChannelRecord channelRecord;
        private final String tagAddr;

        public TagRecord(ChannelRecord channelRecord, String tagAddr) {
            this.channelRecord = channelRecord;
            this.tagAddr = tagAddr;
        }
    }

    @ObjectClassDefinition(id = "com.yofc.plc4x.driver.Plc4xDriver", name = "%name", description = "%description", localization = "OSGI-INF/l10n/com.yofc.plc4x.driver.Plc4xDriverCfg")
    public @interface Config {

        @AttributeDefinition(name = "%connectString", description = "%connectStringDesc")
        String connectString() default "s7://127.0.0.1?remote-device-group=PG_OR_PC&remote-rack=0&remote-slot=0";
    }
}
