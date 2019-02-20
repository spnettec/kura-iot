package com.yofc.iot.component.impl;

import static java.util.Objects.requireNonNull;
import static org.eclipse.kura.channel.ChannelFlag.FAILURE;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

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
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
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

import com.yofc.iot.component.api.H2MVStoreChannelDescriptor;
import com.yofc.iot.component.api.Request;
import com.yofc.iot.component.api.WriteParams;
import com.yofc.iot.component.impl.H2MVStoreDriver.Config;

@Designate(ocd = Config.class, factory = true)
@Component(immediate = true, enabled = true, configurationPolicy = ConfigurationPolicy.REQUIRE, property = {
        "service.pid=com.yofc.iot.driver.H2MVStoreDriver" })
public class H2MVStoreDriver implements Driver, ConfigurableComponent {

    private static final Logger logger = LoggerFactory.getLogger(H2MVStoreDriver.class);
    private H2MVStoreDriverState state = new H2MVStoreDriverState(null, null);
    private final AtomicReference<Config> options = new AtomicReference<>();

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
        try {
            final Config currentOptions = this.options.get();

            if (this.state.options != currentOptions) {
                logger.info("configuration changed, disconnecting...");
                disconnect();
                MVStore s = MVStore.open(getFilename(currentOptions.file()));
                s.setVersionsToKeep(0);
                MVMap<String, Object> map = s.openMap(currentOptions.data());
                this.state = new H2MVStoreDriverState(currentOptions, map);
                logger.info("configuration changed, disconnecting...Done");
            }

        } catch (Exception e) {
            throw new ConnectionException("Connection failed, unexpected exception", e);
        }

    }

    @Override
    public void disconnect() throws ConnectionException {
        if (this.state.client != null && !this.state.client.getStore().isClosed()) {
            logger.debug("Disconnecting from AB PLC...");
            try {
                this.state.client.getStore().close();
                this.state = new H2MVStoreDriverState(null, null);

            } catch (Exception e) {
                throw new ConnectionException(e);
            }
            logger.debug("Disconnecting from H2MVStoreDriver ... Done");
        }

    }

    @Override
    public ChannelDescriptor getChannelDescriptor() {
        return new H2MVStoreChannelDescriptor();
    }

    @Override
    public void read(List<ChannelRecord> records) throws ConnectionException {
        final String[] result = new String[records.size()];
        Map<String, ChannelRecord> tagRecordMap = new HashMap<>();
        for (int i = 0; i < records.size(); i++) {
            ChannelRecord record = records.get(i);
            String tagName = null;
            try {
                tagName = getStringProperty(record, H2MVStoreChannelDescriptor.DATA_TAG_NAME_ID,
                        "H2MVStore Tag setting error");
            } catch (KuraException e) {
                record.setChannelStatus(new ChannelStatus(ChannelFlag.FAILURE, e.getMessage(), e));
                record.setTimestamp(System.currentTimeMillis());
                continue;
            }
            if (tagName == null) {
                record.setChannelStatus(new ChannelStatus(ChannelFlag.FAILURE, "H2MVStore Tag is null",
                        new KuraException(KuraErrorCode.CONFIGURATION_ERROR)));
                record.setTimestamp(System.currentTimeMillis());
            } else {
                result[i] = tagName;
                tagRecordMap.put(tagName, record);
            }
        }
        String[] tagNames = tagRecordMap.keySet().toArray(new String[0]);
        try {
            if (this.state.client == null || this.state.client.getStore().isClosed()) {
                connect();
            }
            for (int i = 0; i < tagNames.length; i++) {
                String tagName = tagNames[i];
                Object value;
                ChannelRecord record = tagRecordMap.get(tagName);
                try {
                    if (!this.state.client.containsKey(tagName)) {
                        value = map(null, record.getValueType()).getValue();
                    } else
                        value = this.state.client.get(tagName);
                    fillRecord(value, record);
                } catch (Exception e) {
                    record.setChannelStatus(new ChannelStatus(ChannelFlag.FAILURE, e.getMessage(), e));
                    record.setTimestamp(System.currentTimeMillis());
                }

            }
        } catch (Exception e) {
            for (int i = 0; i < tagNames.length; i++) {
                String tagName = tagNames[i];
                ChannelRecord record = tagRecordMap.get(tagName);
                record.setChannelStatus(new ChannelStatus(ChannelFlag.FAILURE, e.getMessage(), e));
                record.setTimestamp(System.currentTimeMillis());
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

    @Override
    public void write(List<ChannelRecord> records) throws ConnectionException {
        List<Request<WriteParams>> requests = Request.extractWriteRequests(records);
        try {
            if (this.state.client == null || this.state.client.isClosed()) {
                connect();
            }
            for (int i = 0; i < requests.size(); i++) {
                Request<WriteParams> request = requests.get(i);
                WriteParams para = request.getParameters();

                String tagName = para.getWriteTag();
                Object data = para.getWriteValue();
                this.state.client.put(tagName, data);
                ChannelRecord record = request.getRecord();
                record.setTimestamp(System.currentTimeMillis());
                record.setChannelStatus(new ChannelStatus(ChannelFlag.SUCCESS));

            }
            for (Request<WriteParams> request : requests) {
                ChannelRecord record = request.getRecord();
                record.setTimestamp(System.currentTimeMillis());
                record.setChannelStatus(new ChannelStatus(ChannelFlag.SUCCESS));
            }
            this.state.client.getStore().commit();
        } catch (Exception e) {
            for (Request<WriteParams> request : requests) {
                ChannelRecord record = request.getRecord();
                record.setChannelStatus(new ChannelStatus(ChannelFlag.FAILURE, e.getMessage(), e));
            }
            this.state.client.getStore().rollback();
            throw new ConnectionException(e);
        }

    }

    @Override
    public PreparedRead prepareRead(List<ChannelRecord> records) {
        return new H2MVStorePreparedRead(records);
    }

    private static String getStringProperty(ChannelRecord record, String propertyName, String failureMessage)
            throws KuraException {
        try {
            return String.valueOf(record.getChannelConfig().get(propertyName).toString());
        } catch (Exception e) {
            throw new KuraException(KuraErrorCode.CONFIGURATION_ERROR, failureMessage);
        }
    }

    public static void fillRecord(final Object value, final ChannelRecord record) {
        record.setTimestamp(System.currentTimeMillis());

        if (value == null) {
            record.setChannelStatus(new ChannelStatus(FAILURE, "Operation Result cannot be null", null));
            return;
        }
        record.setTimestamp(System.currentTimeMillis());
        try {
            record.setValue(map(value, record.getValueType()));
            record.setChannelStatus(new ChannelStatus(ChannelFlag.SUCCESS));
        } catch (Exception e) {
            record.setChannelStatus(new ChannelStatus(FAILURE,
                    "Error while converting the retrieved value to the defined typed", null));
        }

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
            return TypedValues.newIntegerValue(((Number) (value == null ? 0 : value)).intValue());
        case BOOLEAN: {
            if (value instanceof Number)
                return TypedValues.newBooleanValue(((Number) (value == null ? 0 : value)).intValue() > 0);
            else if (value instanceof Boolean)
                return TypedValues.newBooleanValue((Boolean) (value == null ? false : value));
            else
                return TypedValues.newBooleanValue(Boolean.parseBoolean(value == null ? "" : (String) value));
        }
        case STRING:
            return TypedValues.newStringValue(value == null ? "" : (String) value);
        case BYTE_ARRAY:
            return TypedValues.newByteArrayValue((byte[]) (value == null ? (new byte[] {}) : value));
        default:
            throw new IllegalArgumentException();
        }
    }

    static final class H2MVStoreDriverState {

        private final MVMap<String, Object> client;
        private final Config options;

        public H2MVStoreDriverState(final Config options, final MVMap<String, Object> client) {
            this.options = options;
            this.client = client;
        }
    }

    private class H2MVStorePreparedRead implements PreparedRead {

        private final List<ChannelRecord> channelRecords;

        public H2MVStorePreparedRead(final List<ChannelRecord> records) {
            this.channelRecords = records;
        }

        @Override
        public List<ChannelRecord> execute() throws ConnectionException {
            try {
                H2MVStoreDriver.this.read(this.channelRecords);
                return Collections.unmodifiableList(this.channelRecords);
            } catch (Exception e) {
                throw new ConnectionException(e);
            }
        }

        @Override
        public List<ChannelRecord> getChannelRecords() {
            return Collections.unmodifiableList(this.channelRecords);
        }

        @Override
        public void close() {
            // no need
        }
    }

    @ObjectClassDefinition(id = "com.yofc.iot.component.impl.H2MVStoreDriver", name = "%name", description = "%description", localization = "OSGI-INF/l10n/com.yofc.iot.component.impl.H2MVStoreDriverCfg")
    @interface Config {

        @AttributeDefinition(name = "%file", description = "%fileDesc", required = false)
        String file() default "/var/kura/tagdata";

        @AttributeDefinition(name = "%data", description = "%dataDesc")
        String data() default "data";
    }

    private static String getFilename(String fileName) throws IOException {
        Path p1 = FileSystems.getDefault().getPath(fileName);
        if (p1.toFile().exists())
            return fileName;
        if (p1.getParent() != null) // null will be returned if the path has no parent
            Files.createDirectories(p1.getParent());
        try {
            Files.createFile(p1);
        } catch (FileAlreadyExistsException e) {
        }
        return fileName;

    }

}
