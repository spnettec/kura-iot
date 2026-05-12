package com.yofc.plc4x.driver;

import com.yofc.plc4x.driver.Plc4xDriver.Config;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.PlcConnectionManager;
import org.apache.plc4x.java.api.messages.*;
import org.apache.plc4x.java.api.model.PlcSubscriptionHandle;
import org.apache.plc4x.java.api.types.PlcResponseCode;
import org.apache.plc4x.java.api.value.PlcValue;
import org.apache.plc4x.java.spi.values.PlcBYTE;
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
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static java.util.Objects.requireNonNull;

@Designate(ocd = Config.class, factory = true)
@Component(immediate = true, enabled = true, configurationPolicy = ConfigurationPolicy.REQUIRE)
public class Plc4xDriver implements Driver, ConfigurableComponent {

    private static final Logger logger = LoggerFactory.getLogger(Plc4xDriver.class);
    private static final long REQUEST_TIMEOUT_MS = 10_000L;
    private final Map<ChannelListener, List<PlcSubscriptionHandle>> registeredListeners = Collections
            .synchronizedMap(new HashMap<>());

    private final AtomicReference<Config> options = new AtomicReference<>();

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

        extractProperties(properties);

        logger.info("Updating Plc4xDriver Driver... Done");
    }

    @Deactivate
    public synchronized void deactivate() {

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
    public void read(List<ChannelRecord> records) {
        Map<String, TagRecord> tagRecords = getTagRecords(records);
        if (tagRecords.isEmpty()) {
            return;
        }
        executeRead(records, tagRecords);
    }

    private void executeRead(List<ChannelRecord> records, Map<String, TagRecord> tagRecords) {
        try (PlcConnection plcConnection = plcConnectionManager.getConnection(this.options.get().connectString())) {
            PlcReadRequest.Builder builder = plcConnection.readRequestBuilder();
            tagRecords.forEach((tagName, tagRecord) -> builder.addTagAddress(tagName, tagRecord.tagAddr));
            PlcReadRequest readRequest = builder.build();
            PlcReadResponse response = readRequest.execute().get(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
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
                    continue;
                }
                try {
                    channelRecord.setValue(map(response.getObject(field), channelRecord.getValueType()));
                    channelRecord.setChannelStatus(new ChannelStatus(ChannelFlag.SUCCESS));
                } catch (Exception e) {
                    channelRecord.setChannelStatus(new ChannelStatus(ChannelFlag.FAILURE, e.getMessage(), e));
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

            PlcWriteResponse response = writeRequest.execute().get(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
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
        PlcSubscriptionResponse subscriptionResponse = null;
        try (PlcConnection plcConnection = plcConnectionManager.getConnection(this.options.get().connectString())) {
            final PlcSubscriptionRequest.Builder sbuilder = plcConnection.subscriptionRequestBuilder();
            for (Entry<ChannelListener, Map<String, Object>> entry : listenerChannelConfigs.entrySet()) {
                Map<String, Object> channelConfig = entry.getValue();
                final String channelName = getRequiredConfigProperty(channelConfig, "+name");
                final String tagAddr = getRequiredConfigProperty(channelConfig,
                        Plc4xChannelDescriptor.DATA_TAG_ADDR_ID);
                final long samplingInterval = Long.parseLong(getRequiredConfigProperty(channelConfig,
                        Plc4xChannelDescriptor.LISTEN_SAMPLING_INTERVAL_PROP_NAME));
                sbuilder.addChangeOfStateTagAddress(channelName, tagAddr, Duration.ofMillis(samplingInterval));
            }
            PlcSubscriptionRequest subscriptionRequest = sbuilder.build();

            subscriptionResponse = subscriptionRequest.execute().get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConnectionException(e);
        } catch (Exception e) {
            throw new ConnectionException(e);
        }
        for (String tagName : subscriptionResponse.getTagNames()) {
            final PlcSubscriptionHandle subscriptionHandle = subscriptionResponse.getSubscriptionHandle(tagName);
            ChannelListener listener = findListener(listenerChannelConfigs, tagName);
            if (listener == null) {
                logger.warn("PLC subscription returned unexpected tag {}", tagName);
                continue;
            }
            List<PlcSubscriptionHandle> handles = registeredListeners.computeIfAbsent(listener,
                    ignored -> new ArrayList<>());
            if (!handles.contains(subscriptionHandle)) {
                subscriptionHandle.register(new ValueChangeHandler(listenerChannelConfigs));
                handles.add(subscriptionHandle);
            } else {
                logger.debug("tagName:{} already register", tagName);
            }
        }
    }

    private static ChannelListener findListener(Map<ChannelListener, Map<String, Object>> listenerChannelConfigs,
            String tagName) {
        return listenerChannelConfigs.entrySet().stream().filter(entry -> {
            Object channelName = entry.getValue().get("+name");
            return tagName.equals(channelName);
        }).map(Entry::getKey).findFirst().orElse(null);
    }

    private static class ValueChangeHandler implements Consumer<PlcSubscriptionEvent> {

        final Map<ChannelListener, Map<String, Object>> listenerChannelConfigs;

        public ValueChangeHandler(Map<ChannelListener, Map<String, Object>> listenerChannelConfigs) {
            this.listenerChannelConfigs = listenerChannelConfigs;
        }

        @Override
        public void accept(PlcSubscriptionEvent plcSubscriptionEvent) {
            logger.debug("Incoming event:");
            for (String fieldName : plcSubscriptionEvent.getTagNames()) {
                Optional<Entry<ChannelListener, Map<String, Object>>> entryOption = listenerChannelConfigs.entrySet()
                        .stream().filter(en -> {
                            Map<String, Object> channelConfig = en.getValue();
                            final String channelName = getRequiredConfigProperty(channelConfig, "+name");
                            return channelName.equals(fieldName);
                        }).findFirst();
                final PlcValue plcValue = plcSubscriptionEvent.getPlcValue(fieldName);
                if (entryOption.isPresent()) {
                    Entry<ChannelListener, Map<String, Object>> entry = entryOption.get();
                    ChannelListener channelListener = entry.getKey();

                    Map<String, Object> config = entry.getValue();
                    final DataType valueType = DataType.valueOf(getRequiredConfigProperty(config, "+value.type"));

                    if (plcValue.isStruct()) {
                        logger.warn("Not support struct value: {}", plcValue);
                    } else {
                        ChannelRecord channelRecord = ChannelRecord.createReadRecord(fieldName, valueType);
                        channelRecord.setTimestamp(System.currentTimeMillis());
                        TypedValue<?> value = null;
                        try {
                            value = map(plcValue.getObject(), valueType);
                            channelRecord.setValue(value);
                            channelRecord.setChannelStatus(new ChannelStatus(ChannelFlag.SUCCESS));
                        } catch (Exception e) {
                            channelRecord.setChannelStatus(new ChannelStatus(ChannelFlag.FAILURE, e.toString(), null));
                        }
                        channelListener.onChannelEvent(new ChannelEvent(channelRecord));
                        // logger.info("Field {} value: {}", fieldName, plcValue.getString());

                    }

                } else {
                    logger.warn("Unknow field {} value: {}", fieldName, plcValue.getString());
                }
            }
        }
    }

    @Override
    public void unregisterChannelListeners(final Collection<ChannelListener> listeners) throws ConnectionException {
        if (registeredListeners.isEmpty()) {
            return;
        }
        List<PlcSubscriptionHandle> handles = listeners.stream().map(registeredListeners::get).filter(Objects::nonNull)
                .flatMap(Collection::stream).toList();
        if (handles.isEmpty()) {
            return;
        }
        try (PlcConnection plcConnection = plcConnectionManager.getConnection(this.options.get().connectString())) {
            PlcUnsubscriptionRequest.Builder builder = plcConnection.unsubscriptionRequestBuilder();

            builder.addHandles(handles);

            builder.build().execute().get();
            listeners.forEach(registeredListeners::remove);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConnectionException(e);
        } catch (Exception e) {
            throw new ConnectionException(e);
        }

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
        Object newValue = value;
        if (value instanceof byte[] bytes) {
            newValue = IntStream.range(0, bytes.length).mapToObj(i -> bytes[i]).map(PlcBYTE::of).toList();
        }
        switch (targetType) {
        case LONGS:
            return TypedValues.newLongValues(getLongs(newValue));
        case LONG: {
            if (newValue instanceof LocalDate) {
                return TypedValues.newLongValue(((LocalDate) newValue).toEpochDay());
            }
            if (newValue instanceof Duration) {
                return TypedValues.newLongValue(((Duration) newValue).toNanos());
            }
            if (newValue instanceof Date) {
                return TypedValues.newLongValue(((Date) newValue).getTime());
            }
            return TypedValues.newLongValue(((Number) (newValue == null ? 0 : newValue)).longValue());
        }
        case FLOATS:
            return TypedValues.newFloatValues(getFloats(newValue));
        case FLOAT:
            return TypedValues.newFloatValue(((Number) (newValue == null ? 0 : newValue)).floatValue());
        case DOUBLES:
            return TypedValues.newDoubleValues(getDoubles(newValue));
        case DOUBLE:
            return TypedValues.newDoubleValue(((Number) (newValue == null ? 0 : newValue)).doubleValue());
        case INTEGERS:
            return TypedValues.newIntegerValues(getIntegers(newValue));
        case BIGINTEGERS:
            return TypedValues.newBigIntegerValues(getBigIntegers(newValue));
        case BOOLEANS:
            return TypedValues.newBooleanValues(getBooleans(newValue));
        case INTEGER:
            if (newValue instanceof Boolean) {
                return TypedValues.newIntegerValue(((Boolean) newValue) ? 1 : 0);
            }
            return TypedValues.newIntegerValue(((Number) (newValue == null ? 0 : newValue)).intValue());
        case BOOLEAN: {
            if (newValue instanceof Number) {
                return TypedValues.newBooleanValue(((Number) newValue).intValue() > 0);
            } else if (newValue instanceof Boolean) {
                return TypedValues.newBooleanValue((Boolean) newValue);
            } else {
                return TypedValues.newBooleanValue(Boolean.parseBoolean(newValue == null ? "" : (String) newValue));
            }
        }
        case STRINGS:
            return TypedValues.newStringValues(getStrings(newValue));
        case STRING:
            if (newValue instanceof List) {
                return TypedValues.newStringValue(ArrayUtils.toString(newValue));
            }
            return TypedValues.newStringValue(newValue == null ? "" : newValue.toString());
        case BYTE_ARRAY:
            if (newValue == null) {
                return TypedValues.newByteArrayValue(new byte[] {});
            }
            @SuppressWarnings("unchecked")
            List<PlcValue> list = (List<PlcValue>) newValue;
            Byte[] bytes = list.stream().map(b -> {
                byte recivedID = 0;
                if (b.isList()) {
                    for (int i = 0; i < 8; i++) {
                        recivedID |= (byte) (b.getIndex(i).getInt() << i);
                    }
                } else {
                    recivedID = b.getByte();
                }
                return recivedID;
            }).toArray(Byte[]::new);
            return TypedValues.newByteArrayValue(ArrayUtils.toPrimitive(bytes));
        default:
            throw new IllegalArgumentException();
        }
    }

    private static Float[] getFloats(Object value) {
        @SuppressWarnings("unchecked")
        List<PlcValue> list = (List<PlcValue>) value;
        return list.stream().map(PlcValue::getFloat).toArray(Float[]::new);
    }

    private static Boolean[] getBooleans(Object value) {
        @SuppressWarnings("unchecked")
        List<PlcValue> list = (List<PlcValue>) value;
        return list.stream().map(PlcValue::getBoolean).toArray(Boolean[]::new);
    }

    private static Double[] getDoubles(Object value) {
        @SuppressWarnings("unchecked")
        List<PlcValue> list = (List<PlcValue>) value;
        return list.stream().map(PlcValue::getDouble).toArray(Double[]::new);
    }

    private static Integer[] getIntegers(Object value) {
        @SuppressWarnings("unchecked")
        List<PlcValue> list = (List<PlcValue>) value;
        return list.stream().map(PlcValue::getInteger).toArray(Integer[]::new);
    }

    private static Long[] getLongs(Object value) {
        @SuppressWarnings("unchecked")
        List<PlcValue> list = (List<PlcValue>) value;
        return list.stream().map(PlcValue::getLong).toArray(Long[]::new);
    }

    private static BigInteger[] getBigIntegers(Object value) {
        @SuppressWarnings("unchecked")
        List<PlcValue> list = (List<PlcValue>) value;
        return list.stream().map(PlcValue::getBigInteger).toArray(BigInteger[]::new);
    }

    private static String[] getStrings(Object value) {
        @SuppressWarnings("unchecked")
        List<PlcValue> list = (List<PlcValue>) value;
        return list.stream().map(PlcValue::getString).toArray(String[]::new);
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

        @AttributeDefinition(name = "%connectString", description = "%connectStringDesc") String connectString() default "s7://127.0.0.1?remote-device-group=PG_OR_PC&remote-rack=0&remote-slot=0";
    }
}
