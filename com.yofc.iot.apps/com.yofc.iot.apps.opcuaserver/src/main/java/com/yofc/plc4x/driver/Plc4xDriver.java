package com.yofc.plc4x.driver;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.apache.plc4x.java.PlcDriverManager;
import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.messages.PlcReadRequest;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.apache.plc4x.java.api.messages.PlcWriteRequest;
import org.apache.plc4x.java.api.messages.PlcWriteResponse;
import org.eclipse.kura.KuraErrorCode;
import org.eclipse.kura.KuraException;
import org.eclipse.kura.channel.ChannelFlag;
import org.eclipse.kura.channel.ChannelRecord;
import org.eclipse.kura.channel.ChannelStatus;
import org.eclipse.kura.channel.listener.ChannelListener;
import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.driver.ChannelDescriptor;
import org.eclipse.kura.driver.Driver;
import org.eclipse.kura.driver.PreparedRead;
import org.eclipse.kura.type.DataType;
import org.eclipse.kura.type.TypedValue;
import org.eclipse.kura.type.TypedValues;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yofc.plc4x.driver.Plc4xDriver.Config;

@Designate(ocd = Config.class, factory = true)
@Component(immediate = true, enabled = true, configurationPolicy = ConfigurationPolicy.REQUIRE, property = {
        "service.pid=com.yofc.plc4x.driver.Plc4xDriver" })
public class Plc4xDriver implements Driver, ConfigurableComponent {

    private static final Logger logger = LoggerFactory.getLogger(Plc4xDriver.class);

    private final AtomicReference<Config> options = new AtomicReference<>();
    private Plc4xDriverState state = new Plc4xDriverState(null, null);

    @Activate
    protected synchronized void activate(final Config properties) {
        logger.info("Activating H2MVStoreDriver...");
        extractProperties(properties);
        logger.info("Activating H2MVStoreDriver... Done");
    }

    private void extractProperties(final Config properties) {
        requireNonNull(properties, "Properties cannot be null");
        this.options.set(properties);
    }

    @Modified
    public synchronized void updated(final Config properties) {
        logger.info("Updating H2MVStoreDriver ...");

        extractProperties(properties);

        try {
            disconnect();
            connect();
        } catch (ConnectionException e) {
            logger.warn("Unable to Disconnect H2MVStoreDriver ...");
        }

        logger.info("Updating H2MVStoreDriver Driver... Done");
    }

    @Deactivate
    public synchronized void deactivate() {
        logger.debug("Deactivating H2MVStoreDriver ...");
        try {
            disconnect();
        } catch (final ConnectionException e) {
            logger.error("Error while disconnecting...", e);
        }
        logger.debug("Deactivating H2MVStoreDriver .....Done");
    }

    @Override
    public void connect() throws ConnectionException {
        final Config currentOptions = this.options.get();

        if (this.state.options != currentOptions) {
            logger.info("configuration changed, disconnecting...");
            reConnect();
            logger.info("configuration changed, disconnecting...Done");
        }

    }

    private void reConnect() throws ConnectionException {
        final Config currentOptions = this.options.get();
        try {
            disconnect();
            BundleContext bundleContext = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
            ClassLoader classloader = bundleContext.getBundle().adapt(BundleWiring.class).getClassLoader();
            PlcConnection plcConnection = new PlcDriverManager(classloader)
                    .getConnection(currentOptions.connectString());

            this.state = new Plc4xDriverState(plcConnection, currentOptions);
        } catch (Exception e) {
            throw new ConnectionException(e.toString());
        }
    }

    @Override
    public void disconnect() throws ConnectionException {
        if (this.state.plcConnection != null && this.state.plcConnection.isConnected()) {
            try {
                if (this.state.plcConnection != null) {
                    this.state.plcConnection.close();
                }
                this.state = new Plc4xDriverState(null, null);
            } catch (Exception e) {
                throw new ConnectionException(e);
            }
            logger.debug("Disconnecting from H2MVStoreDriver ... Done");
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
            return;
        }
        Map<ChannelRecord, TagAddr> map = getTagMap(records);
        PlcReadRequest.Builder builder = this.state.plcConnection.readRequestBuilder();
        map.entrySet().stream().forEach(entry -> {
            if (entry.getKey().getChannelStatus() == null
                    || entry.getKey().getChannelStatus().getChannelFlag() != ChannelFlag.FAILURE) {
                builder.addItem(entry.getValue().tagName, entry.getValue().tagAddr);
            }
        });
        PlcReadRequest readRequest = builder.build();
        try {
            PlcReadResponse response = readRequest.execute().get(10000L, TimeUnit.MILLISECONDS);
            response.getFieldNames().forEach(field -> {
                Optional<Entry<ChannelRecord, TagAddr>> result = map.entrySet().stream().filter(entry -> {
                    return field.equals(entry.getValue().tagName);
                }).findFirst();
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
            });
        } catch (Exception e) {
            records.stream().forEach(channelRecord -> {
                channelRecord.setChannelStatus(new ChannelStatus(ChannelFlag.FAILURE, e.getMessage(), e));
                channelRecord.setTimestamp(System.currentTimeMillis());
            });
        }
    }

    @Override
    public void write(List<ChannelRecord> records) throws ConnectionException {
        if (this.state.plcConnection == null || !this.state.plcConnection.isConnected()) {
            reConnect();
        }
        if (!this.state.plcConnection.getMetadata().canWrite()) {
            Map<ChannelRecord, TagAddr> map = getTagMap(records);
            PlcWriteRequest.Builder builder = this.state.plcConnection.writeRequestBuilder();
            map.entrySet().stream().forEach(entry -> {
                if (entry.getKey().getChannelStatus() == null
                        || entry.getKey().getChannelStatus().getChannelFlag() != ChannelFlag.FAILURE) {
                    builder.addItem(entry.getValue().tagName, entry.getValue().tagAddr);
                }
            });
            PlcWriteRequest writeRequest = builder.build();
            try {
                PlcWriteResponse response = writeRequest.execute().get(10000L, TimeUnit.MILLISECONDS);
                response.getFieldNames().forEach(field -> {
                    Optional<Entry<ChannelRecord, TagAddr>> result = map.entrySet().stream().filter(entry -> {
                        return field.equals(entry.getValue().tagName);
                    }).findFirst();
                    ChannelRecord channelRecord = result.get().getKey();
                    channelRecord.setTimestamp(System.currentTimeMillis());
                    if (result.isPresent()) {
                        channelRecord.setChannelStatus(new ChannelStatus(ChannelFlag.SUCCESS));

                    } else {
                        channelRecord
                                .setChannelStatus(new ChannelStatus(ChannelFlag.FAILURE, "unknow write error", null));
                    }
                });
            } catch (Exception e) {
                records.stream().forEach(channelRecord -> {
                    channelRecord.setChannelStatus(new ChannelStatus(ChannelFlag.FAILURE, e.getMessage(), e));
                    channelRecord.setTimestamp(System.currentTimeMillis());
                });
            }
        }

    }

    @Override
    public void registerChannelListener(Map<String, Object> channelConfig, ChannelListener listener)
            throws ConnectionException {
        logger.info("{}", channelConfig);

    }

    @Override
    public void unregisterChannelListener(ChannelListener listener) throws ConnectionException {
        logger.info("{}", listener);

    }

    private Map<ChannelRecord, TagAddr> getTagMap(List<ChannelRecord> records) {
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
            return new TagAddr(tagName, tagAddr);
        }));
    }

    @Override
    public PreparedRead prepareRead(List<ChannelRecord> records) {
        return null;
    }

    private static TypedValue<?> map(final Object value, final DataType targetType) throws Exception {
        switch (targetType) {
        case LONG:
            return TypedValues.newLongValue(((Number) (value == null ? 0 : value)).longValue());
        case FLOAT:
            return TypedValues.newFloatValue(((Number) (value == null ? 0 : value)).floatValue());
        case DOUBLE:
            return TypedValues.newDoubleValue(((Number) (value == null ? 0 : value)).doubleValue());
        case INTEGER:
            if (value instanceof Boolean) {
                return TypedValues.newIntegerValue(((Boolean) value) == Boolean.TRUE ? 1 : 0);
            }
            return TypedValues.newIntegerValue(((Number) (value == null ? 0 : value)).intValue());
        case BOOLEAN: {
            if (value instanceof Number) {
                return TypedValues.newBooleanValue(((Number) (value == null ? 0 : value)).intValue() > 0);
            } else if (value instanceof Boolean) {
                return TypedValues.newBooleanValue((Boolean) (value == null ? false : value));
            } else {
                return TypedValues.newBooleanValue(Boolean.parseBoolean(value == null ? "" : (String) value));
            }
        }
        case STRING:
            return TypedValues.newStringValue(value == null ? "" : (String) value);
        case BYTE_ARRAY:
            return TypedValues.newByteArrayValue((byte[]) (value == null ? (new byte[] {}) : value));
        default:
            throw new IllegalArgumentException();
        }
    }

    static final class TagAddr {

        private final String tagName;
        private final String tagAddr;

        public TagAddr(String tagName, String tagAddr) {
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
        String connectString() default "s7://127.0.0.1?remote-rack=0&remote-slot=2";
    }
}
