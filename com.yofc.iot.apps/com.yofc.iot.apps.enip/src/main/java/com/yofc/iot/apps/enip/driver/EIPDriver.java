/**
 * Copyright (c) 2017, 2018 Eurotech and/or its affiliates
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Eurotech
 */

package com.yofc.iot.apps.enip.driver;

import static java.util.Objects.requireNonNull;
import static org.eclipse.kura.channel.ChannelFlag.FAILURE;

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

import com.yofc.iot.apps.enip.cip.structs.CIPData;
import com.yofc.iot.apps.enip.driver.EIPDriver.Config;

@Designate(ocd = Config.class, factory = true)
@Component(enabled = true, configurationPolicy = ConfigurationPolicy.REQUIRE, property = {
        "service.pid=com.yofc.iot.apps.enip.driver.EIPDriver" })
public class EIPDriver implements Driver, ConfigurableComponent {

    private static final Logger logger = LoggerFactory.getLogger(EIPDriver.class);
    private EIPClientState state = new EIPClientState(null);
    private final AtomicReference<Config> options = new AtomicReference<>();

    @Activate
    protected synchronized void activate(final Config properties) {
        logger.info("Activating EIP-PLC Driver...");
        extractProperties(properties);
        logger.info("Activating EIP-PLC Driver... Done");
    }

    private void extractProperties(final Config properties) {
        requireNonNull(properties, "Properties cannot be null");
        this.options.set(properties);
    }

    @Modified
    public synchronized void updated(final Config properties) throws ConnectionException {
        logger.info("Updating EIP-PLC Driver...");
        disconnect();
        extractProperties(properties);

        logger.info("Updating EIP-PLC Driver... Done");
    }

    @Deactivate
    public synchronized void deactivate() {
        logger.debug("Deactivating EIP PLC Driver...");
        try {
            disconnect();
        } catch (final ConnectionException e) {
            logger.error("Error while disconnecting...", e);
            return;
        }
        logger.debug("Deactivating EIP PLC Driver.....Done");
    }

    @Override
    public void connect() throws ConnectionException {
        try {
            final Config currentOptions = this.options.get();

            if (this.state.client == null || this.state.options != currentOptions) {
                logger.info("configuration changed, disconnecting...");
                disconnect();
                this.state = createClientState(currentOptions);
                logger.info("configuration changed, disconnecting...Done");
            }

            if (!this.state.client.isConnected()) {
                logger.debug("Connecting to EIP PLC...");
                this.state.client.setTimeoutMs(this.state.options.timeout());
                this.state.client.connect();
                logger.debug("Connecting to EIP PLC... Done");
            }
        } catch (Exception e) {
            throw new ConnectionException("EIP PLC Connection failed, unexpected exception", e);
        }
    }

    protected EIPClientState createClientState(final Config options) {
        return new EIPClientState(options);
    }

    @Override
    public void disconnect() throws ConnectionException {
        if (this.state.client != null && this.state.client.isConnected()) {
            logger.debug("Disconnecting from EIP PLC...");
            try {
                this.state.client.close();
                state = new EIPClientState(null);
            } catch (Exception e) {
                throw new ConnectionException(e);
            }
            logger.debug("Disconnecting from EIP PLC... Done");
        }

    }

    @Override
    public ChannelDescriptor getChannelDescriptor() {
        return new EIPChannelDescriptor();
    }

    @Override
    public void read(List<ChannelRecord> records) throws ConnectionException {
        if (this.state.client == null || !this.state.client.isConnected()) {
            connect();
        }
        if (this.state.options.readSingle())
            readSingle(records);
        else
            readMuti(records);
    }

    public static void fillRecord(final CIPData value, final ChannelRecord record) {
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
            record.setChannelStatus(
                    new ChannelStatus(FAILURE, "Error while converting the retrieved value to the defined typed", e));
        }

    }

    private static TypedValue<?> map(final CIPData value, final DataType targetType) throws Exception {
        Object valueObj = value.get();
        switch (targetType) {
        case LONG:
            return TypedValues.newLongValue(((Number) valueObj).longValue());
        case FLOAT:
            return TypedValues.newFloatValue(((Number) valueObj).floatValue());
        case DOUBLE:
            return TypedValues.newDoubleValue(((Number) valueObj).doubleValue());
        case INTEGER:
            return TypedValues.newIntegerValue(((Number) valueObj).intValue());
        case BOOLEAN:
            return TypedValues.newBooleanValue(((Number) valueObj).intValue() != 0);
        case STRING:
            return TypedValues.newStringValue(valueObj == null ? "" : (String) valueObj);
        case BYTE_ARRAY:
            return TypedValues.newByteArrayValue((byte[]) valueObj);
        default:
            throw new IllegalArgumentException();
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
        if (this.state.client == null || !this.state.client.isConnected()) {
            connect();
        }
        if (this.state.options.writeSingle())
            writeSingle(records);
        else
            writeMuti(records);
    }

    private void readSingle(List<ChannelRecord> records) throws ConnectionException {
        for (int i = 0; i < records.size(); i++) {
            ChannelRecord record = records.get(i);
            String tagName = null;
            try {
                tagName = getStringProperty(record, EIPChannelDescriptor.DATA_TAG_NAME_ID, "EIP Tag setting error");
            } catch (KuraException e) {
                record.setChannelStatus(new ChannelStatus(ChannelFlag.FAILURE, e.getMessage(), e));
                record.setTimestamp(System.currentTimeMillis());
                continue;
            }
            if (tagName == null) {
                record.setChannelStatus(new ChannelStatus(ChannelFlag.FAILURE, "EIP Tag is null",
                        new KuraException(KuraErrorCode.CONFIGURATION_ERROR)));
                record.setTimestamp(System.currentTimeMillis());
            } else {
                try {
                    CIPData cipData = this.state.client.readTag(tagName).get();
                    fillRecord(cipData, record);
                } catch (Exception e) {
                    logger.error("EIP PLC read error", e);
                    record.setChannelStatus(new ChannelStatus(ChannelFlag.FAILURE, e.getMessage(), e));
                    record.setTimestamp(System.currentTimeMillis());
                    // disconnect();
                    // connect();
                }

            }
        }
    }

    private void readMuti(List<ChannelRecord> records) throws ConnectionException {
        final String[] result = new String[records.size()];
        Map<String, ChannelRecord> tagRecordMap = new HashMap<>();
        for (int i = 0; i < records.size(); i++) {
            ChannelRecord record = records.get(i);
            String tagName = null;
            try {
                tagName = getStringProperty(record, EIPChannelDescriptor.DATA_TAG_NAME_ID, "EIP Tag setting error");
            } catch (KuraException e) {
                record.setChannelStatus(new ChannelStatus(ChannelFlag.FAILURE, e.getMessage(), e));
                record.setTimestamp(System.currentTimeMillis());
                continue;
            }
            if (tagName == null) {
                record.setChannelStatus(new ChannelStatus(ChannelFlag.FAILURE, "EIP Tag is null",
                        new KuraException(KuraErrorCode.CONFIGURATION_ERROR)));
                record.setTimestamp(System.currentTimeMillis());
            } else {
                result[i] = tagName;
                tagRecordMap.put(tagName, record);
            }
        }
        final Config abOptions = this.options.get();
        int maxItemCountPerRequest = abOptions.maxRequestItems();
        String[] tagNames = tagRecordMap.keySet().toArray(new String[0]);
        try {
            CIPData[] cipDatas = this.state.client.readTags(maxItemCountPerRequest, tagNames).get();
            for (int i = 0; i < tagNames.length; i++) {
                String tagName = tagNames[i];
                ChannelRecord record = tagRecordMap.get(tagName);
                fillRecord(cipDatas[i], record);
            }
        } catch (Exception e) {
            logger.error("EIP PLC read error", e);
            for (int i = 0; i < tagNames.length; i++) {
                String tagName = tagNames[i];
                ChannelRecord record = tagRecordMap.get(tagName);
                record.setChannelStatus(new ChannelStatus(ChannelFlag.FAILURE, e.getMessage(), e));
                record.setTimestamp(System.currentTimeMillis());
            }
            // disconnect();
            throw new ConnectionException(e);
        }
    }

    private void writeMuti(List<ChannelRecord> records) throws ConnectionException {
        List<Request<WriteParams>> requests = Request.extractWriteRequests(records);
        String[] tagNames = new String[requests.size()];
        CIPData[] cipData = new CIPData[requests.size()];
        for (int i = 0; i < requests.size(); i++) {
            Request<WriteParams> request = requests.get(i);
            WriteParams para = request.getParameters();

            String tagName = para.getWriteTag();
            tagNames[i] = tagName;
            CIPData data = para.getWriteValue();
            if (data == null)
                throw new ConnectionException("Data Error");
            cipData[i] = data;

        }

        try {
            this.state.client.writeTags(tagNames, cipData);
            for (Request<WriteParams> request : requests) {
                ChannelRecord record = request.getRecord();
                record.setTimestamp(System.currentTimeMillis());
                record.setChannelStatus(new ChannelStatus(ChannelFlag.SUCCESS));
            }
        } catch (Exception e) {
            for (Request<WriteParams> request : requests) {
                ChannelRecord record = request.getRecord();
                record.setChannelStatus(new ChannelStatus(ChannelFlag.FAILURE, e.getMessage(), e));
            }
            // disconnect();
            throw new ConnectionException(e);
        }

    }

    private void writeSingle(List<ChannelRecord> records) throws ConnectionException {
        List<Request<WriteParams>> requests = Request.extractWriteRequests(records);
        for (int i = 0; i < requests.size(); i++) {
            Request<WriteParams> request = requests.get(i);
            WriteParams para = request.getParameters();

            String tagName = para.getWriteTag();
            CIPData data = para.getWriteValue();
            if (data == null)
                throw new ConnectionException("Data Error");

            ChannelRecord record = request.getRecord();
            record.setTimestamp(System.currentTimeMillis());
            try {
                this.state.client.writeTag(tagName, data);

                record.setChannelStatus(new ChannelStatus(ChannelFlag.SUCCESS));
            } catch (Exception e) {

                record.setChannelStatus(new ChannelStatus(ChannelFlag.FAILURE, e.getMessage(), e));

            }

        }
    }

    @Override
    public PreparedRead prepareRead(List<ChannelRecord> records) {
        return new EIPPreparedRead(records);
    }

    private static String getStringProperty(ChannelRecord record, String propertyName, String failureMessage)
            throws KuraException {
        try {
            return String.valueOf(record.getChannelConfig().get(propertyName).toString());
        } catch (Exception e) {
            throw new KuraException(KuraErrorCode.CONFIGURATION_ERROR, failureMessage);
        }
    }

    static final class EIPClientState {

        private final EIPDriverClient client;
        private final Config options;

        EIPClientState(final Config options) {
            this(options,
                    options == null ? null : new EIPDriverClient(options.hostIp(), options.slot(), options.port()));
        }

        public EIPClientState(final Config options, final EIPDriverClient client) {
            this.options = options;
            this.client = client;
        }
    }

    private class EIPPreparedRead implements PreparedRead {

        private final List<ChannelRecord> channelRecords;

        public EIPPreparedRead(final List<ChannelRecord> records) {
            this.channelRecords = records;
        }

        @Override
        public List<ChannelRecord> execute() throws ConnectionException {
            try {
                EIPDriver.this.read(this.channelRecords);
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

    @ObjectClassDefinition(id = "com.yofc.iot.apps.enip.driver.EIPDriver", name = "%name", description = "%description", localization = "OSGI-INF/l10n/com.yofc.iot.apps.enip.driver.Config")
    @interface Config {

        @AttributeDefinition(name = "%hostIp", description = "%hostIpDesc")
        String hostIp() default "localhost";

        @AttributeDefinition(name = "%port", description = "%portDesc")
        int port() default 44818;

        @AttributeDefinition(name = "%slot", description = "%slotDesc")
        int slot() default 0;

        @AttributeDefinition(name = "%timeout", description = "%timeoutDesc")
        int timeout() default 2000;

        @AttributeDefinition(name = "%readSingle", description = "%readSingleDesc")
        boolean readSingle() default false;

        @AttributeDefinition(name = "%writeSingle", description = "%writeSingleDesc")
        boolean writeSingle() default false;

        @AttributeDefinition(name = "%maxRequestItems", description = "%maxRequestItemsDesc")
        int maxRequestItems() default 30;
    }

}
