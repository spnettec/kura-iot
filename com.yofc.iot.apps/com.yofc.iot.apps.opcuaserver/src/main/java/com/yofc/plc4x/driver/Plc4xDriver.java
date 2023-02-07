package com.yofc.plc4x.driver;

import static java.util.Objects.requireNonNull;

import java.math.BigInteger;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.PlcConnectionManager;
import org.apache.plc4x.java.api.messages.PlcReadRequest;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.apache.plc4x.java.api.messages.PlcSubscriptionEvent;
import org.apache.plc4x.java.api.messages.PlcSubscriptionRequest;
import org.apache.plc4x.java.api.messages.PlcSubscriptionResponse;
import org.apache.plc4x.java.api.messages.PlcUnsubscriptionRequest;
import org.apache.plc4x.java.api.messages.PlcWriteRequest;
import org.apache.plc4x.java.api.messages.PlcWriteResponse;
import org.apache.plc4x.java.api.model.PlcSubscriptionHandle;
import org.apache.plc4x.java.api.types.PlcResponseCode;
import org.apache.plc4x.java.api.value.PlcValue;
import org.eclipse.kura.KuraErrorCode;
import org.eclipse.kura.KuraException;
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
import com.yofc.vertx.osgi.api.Utils;

@Designate(ocd = Config.class, factory = true)
@Component(immediate = true, enabled = true, configurationPolicy = ConfigurationPolicy.REQUIRE, property = {
        "service.pid=com.yofc.plc4x.driver.Plc4xDriver" })
public class Plc4xDriver implements Driver, ConfigurableComponent {

    private static final Logger logger = LoggerFactory.getLogger(Plc4xDriver.class);
    private final Map<ChannelListener, PlcSubscriptionHandle> registeredListeners = new ConcurrentHashMap<>();

    private final AtomicReference<Config> options = new AtomicReference<>();
    private Plc4xDriverState state = new Plc4xDriverState(null, null);
    @Reference
    private PlcConnectionManager plcConnectionManager;

    @Activate
    protected synchronized void activate(final Config properties) {
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

        try {
            disconnect();
            connect();
        } catch (ConnectionException e) {
            logger.warn("Unable to Disconnect Plc4xDriver ...");
        }

        logger.info("Updating Plc4xDriver Driver... Done");
    }

    @Deactivate
    public synchronized void deactivate() {
        logger.debug("Deactivating Plc4xDriver ...");
        try {
            disconnect();
        } catch (final ConnectionException e) {
            logger.error("Error while disconnecting...", e);
        }
        logger.debug("Deactivating Plc4xDriver .....Done");
    }

    @Override
    public void connect() throws ConnectionException {
        final Config currentOptions = this.options.get();

        if (this.state.options != currentOptions) {
            logger.info("Plc4xDriver configuration changed, disconnecting...");
            reConnect();
            logger.info("Plc4xDriver configuration changed, disconnecting...Done");
        }

    }

    private void reConnect() throws ConnectionException {
        final Config currentOptions = this.options.get();
        try {
            disconnect();
            PlcConnection plcConnection = plcConnectionManager.getConnection(currentOptions.connectString());

            this.state = new Plc4xDriverState(plcConnection, currentOptions);
        } catch (Exception e) {
            throw new ConnectionException(e);
        }
    }

    @Override
    public void disconnect() throws ConnectionException {
        if (this.state.plcConnection != null) {
            try {
                if (this.state.plcConnection != null) {
                    this.state.plcConnection.close();
                }
                this.state = new Plc4xDriverState(null, null);
            } catch (Exception e) {
                throw new ConnectionException(e);
            }
            logger.debug("Disconnecting from Plc4xDriver ... Done");
        }

    }

    @Override
    public ChannelDescriptor getChannelDescriptor() {
        return new Plc4xChannelDescriptor();
    }

    @Override
    public void read(List<ChannelRecord> records) throws ConnectionException {
        if (this.state.plcConnection == null || !this.state.plcConnection.isConnected()) {
            reConnect();
        }
        if (!this.state.plcConnection.getMetadata().canRead()) {
            throw new ConnectionException("PLC can not read");
        }
        Map<ChannelRecord, TagNameAddr> map = getTagMap(records);
        PlcReadRequest.Builder builder = this.state.plcConnection.readRequestBuilder();
        map.entrySet().stream().forEach(Utils.consumerWrapper(entry -> {
            if (entry.getKey().getChannelStatus() == null
                    || entry.getKey().getChannelStatus().getChannelFlag() != ChannelFlag.FAILURE) {
                builder.addTagAddress(entry.getValue().tagName, entry.getValue().tagAddr);
            }
        }));
        PlcReadRequest readRequest = builder.build();
        try {
            PlcReadResponse response = readRequest.execute().get(10000L, TimeUnit.MILLISECONDS);
            response.getTagNames().forEach(Utils.consumerWrapper(field -> {
                Optional<Entry<ChannelRecord, TagNameAddr>> result = map.entrySet().stream()
                        .filter(entry -> field.equals(entry.getValue().tagName)).findFirst();
                ChannelRecord channelRecord = result.get().getKey();
                if (result.isPresent()) {

                    channelRecord.setTimestamp(System.currentTimeMillis());
                    try {
                        channelRecord.setChannelStatus(new ChannelStatus(ChannelFlag.SUCCESS));
                        channelRecord.setValue(map(response.getObject(field), channelRecord.getValueType()));
                    } catch (Exception e) {
                        channelRecord.setChannelStatus(new ChannelStatus(ChannelFlag.FAILURE, e.getMessage(), e));
                    }
                } else {
                    channelRecord.setChannelStatus(new ChannelStatus(ChannelFlag.FAILURE, "no value found",
                            new RuntimeException("no value found")));
                }
            }));
        } catch (Exception e) {
            records.stream().forEach(channelRecord -> {
                channelRecord.setChannelStatus(new ChannelStatus(ChannelFlag.FAILURE, e.getMessage(), e));
                channelRecord.setTimestamp(System.currentTimeMillis());
            });
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void write(List<ChannelRecord> records) throws ConnectionException {
        if (this.state.plcConnection == null || !this.state.plcConnection.isConnected()) {
            reConnect();
        }
        if (!this.state.plcConnection.getMetadata().canWrite()) {
            throw new ConnectionException("PLC can't Write");
        }

        Map<ChannelRecord, TagNameAddr> map = getTagMap(records);
        PlcWriteRequest.Builder builder = this.state.plcConnection.writeRequestBuilder();
        map.entrySet().stream().forEach(Utils.consumerWrapper(entry -> {
            if (entry.getKey().getChannelStatus() == null
                    || entry.getKey().getChannelStatus().getChannelFlag() != ChannelFlag.FAILURE) {
                builder.addTagAddress(entry.getValue().tagName, entry.getValue().tagAddr,
                        entry.getKey().getValue().getValue());
            }
        }));
        PlcWriteRequest writeRequest = builder.build();
        try {
            PlcWriteResponse response = writeRequest.execute().get(10000L, TimeUnit.MILLISECONDS);
            response.getTagNames().forEach(Utils.consumerWrapper(field -> {
                Optional<Entry<ChannelRecord, TagNameAddr>> result = map.entrySet().stream()
                        .filter(entry -> field.equals(entry.getValue().tagName)).findFirst();
                if (!result.isPresent()) {
                    throw new ConnectionException("PLC Write error");
                }

                ChannelRecord channelRecord = result.get().getKey();
                channelRecord.setTimestamp(System.currentTimeMillis());
                PlcResponseCode responseCode = response.getResponseCode(field);
                if (responseCode == PlcResponseCode.OK) {
                    channelRecord.setChannelStatus(new ChannelStatus(ChannelFlag.SUCCESS));
                } else {
                    channelRecord.setChannelStatus(new ChannelStatus(ChannelFlag.FAILURE, responseCode.name(), null));
                }
            }));
        } catch (Exception e) {
            records.stream().forEach(channelRecord -> {
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
        if (this.state.plcConnection == null || !this.state.plcConnection.isConnected()) {
            reConnect();
        }
        if (!this.state.plcConnection.getMetadata().canSubscribe()) {
            throw new ConnectionException("PLC can not add Listener");
        }
        final PlcSubscriptionRequest.Builder sbuilder = this.state.plcConnection.subscriptionRequestBuilder();
        for (Map.Entry<ChannelListener, Map<String, Object>> entry : listenerChannelConfigs.entrySet()) {
            Map<String, Object> channelConfig = entry.getValue();
            final String channelName = Utils.tryExtract(channelConfig, config -> (String) config.get("+name"),
                    "Error while retrieving Channel Name");
            final String tagAddr = Utils.tryExtract(channelConfig, config -> (String) config.get("data.tag.addr"),
                    "Error while retrieving Tag Addr");
            sbuilder.addChangeOfStateTagAddress(channelName, tagAddr);
        }
        PlcSubscriptionRequest subscriptionRequest = sbuilder.build();
        PlcSubscriptionResponse subscriptionResponse = null;
        try {
            subscriptionResponse = subscriptionRequest.execute().get();
        } catch (ExecutionException e) {
            throw new ConnectionException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConnectionException(e);
        }
        for (String subscriptionName : subscriptionResponse.getTagNames()) {
            final PlcSubscriptionHandle subscriptionHandle = subscriptionResponse
                    .getSubscriptionHandle(subscriptionName);
            Optional<Map.Entry<ChannelListener, Map<String, Object>>> entryOption = listenerChannelConfigs.entrySet()
                    .stream().filter(en -> {
                        Map<String, Object> channelConfig = en.getValue();
                        final String channelName = Utils.tryExtract(channelConfig,
                                config -> (String) config.get("+name"), "Error while retrieving Channel Name");
                        return channelName.equals(subscriptionName);
                    }).findFirst();
            if (entryOption.isPresent()) {
                Map.Entry<ChannelListener, Map<String, Object>> entry = entryOption.get();
                ChannelListener channelListener = entry.getKey();
                if (registeredListeners.computeIfPresent(channelListener, (k, v) -> v) != null) {
                    logger.warn("subscription:{} already exist", subscriptionName);
                    continue;
                }
                Map<String, Object> config = entry.getValue();
                final DataType valueType = Utils.tryExtract(config,
                        con -> DataType.valueOf((String) con.get("+value.type")), "Error while retrieving value type");

                subscriptionHandle.register(new ValueChangeHandler(subscriptionName, valueType, entry.getKey()));
                registeredListeners.put(channelListener, subscriptionHandle);
            }
        }
    }

    private static class ValueChangeHandler implements Consumer<PlcSubscriptionEvent> {

        final String channelName;
        final DataType valueType;

        final ChannelListener listener;

        public ValueChangeHandler(String channelName, DataType valueType, ChannelListener listener) {
            this.channelName = channelName;
            this.valueType = valueType;
            this.listener = listener;
        }

        @Override
        public void accept(PlcSubscriptionEvent plcSubscriptionEvent) {
            logger.info("Incoming event:");
            for (String fieldName : plcSubscriptionEvent.getTagNames()) {
                final PlcValue plcValue = plcSubscriptionEvent.getPlcValue(fieldName);
                if (plcValue.isList()) {
                    logger.warn("Not support list value: {}", plcValue);
                } else if (plcValue.isStruct()) {
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
                    if (fieldName.equals(channelName)) {
                        this.listener.onChannelEvent(new ChannelEvent(channelRecord));
                        logger.info("Field {} value: {}", fieldName, plcValue.getString());
                    } else {
                        logger.warn("Unknow field {} value: {}", fieldName, plcValue.getString());
                    }

                }
            }
        }
    }

    @Override
    public void unregisterChannelListeners(final Collection<ChannelListener> listeners) throws ConnectionException {
        if (this.state.plcConnection == null || !this.state.plcConnection.isConnected()) {
            reConnect();
        }
        if (!this.state.plcConnection.getMetadata().canSubscribe()) {
            throw new ConnectionException("PLC can not add Listener");
        }
        PlcUnsubscriptionRequest.Builder builder = this.state.plcConnection.unsubscriptionRequestBuilder();
        boolean hasHandle = false;
        for (ChannelListener listerner : listeners) {
            PlcSubscriptionHandle plcSubscriptionHandle = registeredListeners.computeIfPresent(listerner, (k, v) -> v);
            if (plcSubscriptionHandle != null) {
                builder.addHandles(plcSubscriptionHandle);
                hasHandle = true;
            }
            registeredListeners.remove(listerner);
        }
        if (hasHandle) {
            try {
                builder.build().execute().get();
            } catch (ExecutionException e) {
                throw new ConnectionException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ConnectionException(e);
            }
        }

    }

    private Map<ChannelRecord, TagNameAddr> getTagMap(List<ChannelRecord> records) {
        return records.stream().collect(Collectors.toMap(channelRecord -> channelRecord, channelRecord -> {
            String tagName = null;
            String tagAddr = null;
            try {
                tagName = channelRecord.getChannelConfig().get("+name").toString();
                tagAddr = channelRecord.getChannelConfig().get(Plc4xChannelDescriptor.DATA_TAG_ADDR_ID).toString();
            } catch (Exception e) {
                channelRecord.setChannelStatus(new ChannelStatus(ChannelFlag.FAILURE, e.getMessage(), e));
                channelRecord.setTimestamp(System.currentTimeMillis());
                return null;
            }
            if (tagName == null || tagAddr == null) {
                channelRecord.setChannelStatus(new ChannelStatus(ChannelFlag.FAILURE, "tagName or tagAddr is null",
                        new KuraException(KuraErrorCode.CONFIGURATION_ERROR)));
                channelRecord.setTimestamp(System.currentTimeMillis());
                return null;
            }
            return new TagNameAddr(tagName, tagAddr);
        }));
    }

    @Override
    public PreparedRead prepareRead(List<ChannelRecord> records) {
        return null;
    }

    private static TypedValue<?> map(final Object value, final DataType targetType) throws Exception {
        switch (targetType) {
        case LONGS:
            return TypedValues.newLongValues(getLongs(value));
        case LONG: {
            if (value instanceof LocalDate) {
                return TypedValues.newLongValue(((LocalDate) value).toEpochDay());
            }
            if (value instanceof Duration) {
                return TypedValues.newLongValue(((Duration) value).toNanos());
            }
            if (value instanceof Date) {
                return TypedValues.newLongValue(((Date) value).getTime());
            }
            return TypedValues.newLongValue(((Number) (value == null ? 0 : value)).longValue());
        }
        case FLOATS:
            return TypedValues.newFloatValues(getFloats(value));
        case FLOAT:
            return TypedValues.newFloatValue(((Number) (value == null ? 0 : value)).floatValue());
        case DOUBLES:
            return TypedValues.newDoubleValues(getDoubles(value));
        case DOUBLE:
            return TypedValues.newDoubleValue(((Number) (value == null ? 0 : value)).doubleValue());
        case INTEGERS:
            return TypedValues.newIntegerValues(getIntegers(value));
        case BIGINTEGERS:
            return TypedValues.newBigIntegerValues(getBigIntegers(value));
        case BOOLEANS:
            return TypedValues.newBooleanValues(getBooleans(value));
        case INTEGER:
            if (value instanceof Boolean) {
                return TypedValues.newIntegerValue(((Boolean) value) == Boolean.TRUE ? 1 : 0);
            }
            return TypedValues.newIntegerValue(((Number) (value == null ? 0 : value)).intValue());
        case BOOLEAN: {
            if (value instanceof Number) {
                return TypedValues.newBooleanValue(((Number) value).intValue() > 0);
            } else if (value instanceof Boolean) {
                return TypedValues.newBooleanValue((Boolean) value);
            } else {
                return TypedValues.newBooleanValue(Boolean.parseBoolean(value == null ? "" : (String) value));
            }
        }
        case STRINGS:
            return TypedValues.newStringValues(getStrings(value));
        case STRING:
            if (value instanceof List) {
                return TypedValues.newStringValue(ArrayUtils.toString(value));
            }
            return TypedValues.newStringValue(value == null ? "" : value.toString());
        case BYTE_ARRAY:
            if (value == null) {
                return TypedValues.newByteArrayValue(new byte[] {});
            }
            @SuppressWarnings("unchecked")
            List<PlcValue> list = (List<PlcValue>) value;
            Byte[] bytes = list.stream().map(b -> {
                byte recivedID = 0;
                if (b.isList()) {
                    for (int i = 0; i < 8; i++) {
                        recivedID |= b.getIndex(i).getInt() << i;
                    }
                } else {
                    recivedID = b.getByte();
                }
                return recivedID;
            }).toArray(size -> new Byte[size]);
            return TypedValues.newByteArrayValue(ArrayUtils.toPrimitive(bytes));
        default:
            throw new IllegalArgumentException();
        }
    }

    private static Float[] getFloats(Object value) {
        @SuppressWarnings("unchecked")
        List<PlcValue> list = (List<PlcValue>) value;
        return list.stream().map(PlcValue::getFloat).toArray(size -> new Float[size]);
    }

    private static Boolean[] getBooleans(Object value) {
        @SuppressWarnings("unchecked")
        List<PlcValue> list = (List<PlcValue>) value;
        return list.stream().map(PlcValue::getBoolean).toArray(size -> new Boolean[size]);
    }

    private static Double[] getDoubles(Object value) {
        @SuppressWarnings("unchecked")
        List<PlcValue> list = (List<PlcValue>) value;
        return list.stream().map(PlcValue::getDouble).toArray(size -> new Double[size]);
    }

    private static Integer[] getIntegers(Object value) {
        @SuppressWarnings("unchecked")
        List<PlcValue> list = (List<PlcValue>) value;
        return list.stream().map(PlcValue::getInteger).toArray(size -> new Integer[size]);
    }

    private static Long[] getLongs(Object value) {
        @SuppressWarnings("unchecked")
        List<PlcValue> list = (List<PlcValue>) value;
        return list.stream().map(PlcValue::getLong).toArray(size -> new Long[size]);
    }

    private static BigInteger[] getBigIntegers(Object value) {
        @SuppressWarnings("unchecked")
        List<PlcValue> list = (List<PlcValue>) value;
        return list.stream().map(PlcValue::getBigInteger).toArray(size -> new BigInteger[size]);
    }

    private static String[] getStrings(Object value) {
        @SuppressWarnings("unchecked")
        List<PlcValue> list = (List<PlcValue>) value;
        return list.stream().map(PlcValue::getString).toArray(size -> new String[size]);
    }

    static final class TagNameAddr {

        private final String tagName;
        private final String tagAddr;

        public TagNameAddr(String tagName, String tagAddr) {
            this.tagName = tagName;
            this.tagAddr = tagAddr;
        }
    }

    static final class Plc4xDriverState {

        private final PlcConnection plcConnection;
        private final Config options;

        public Plc4xDriverState(PlcConnection plcConnection, Config options) {
            this.plcConnection = plcConnection;
            this.options = options;
        }
    }

    @ObjectClassDefinition(id = "com.yofc.plc4x.driver.Plc4xDriver", name = "%name", description = "%description", localization = "OSGI-INF/l10n/com.yofc.plc4x.driver.Plc4xDriverCfg")
    @interface Config {

        @AttributeDefinition(name = "%connectString", description = "%connectStringDesc")
        String connectString() default "s7://127.0.0.1?remote-group=1&remote-rack=0&remote-slot=0";
    }
}
