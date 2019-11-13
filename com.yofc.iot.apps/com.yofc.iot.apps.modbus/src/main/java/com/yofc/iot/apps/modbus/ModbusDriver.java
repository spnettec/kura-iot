package com.yofc.iot.apps.modbus;

import static org.eclipse.kura.channel.ChannelFlag.FAILURE;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.IntStream;

import org.eclipse.kura.KuraConnectionStatus;
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
import org.eclipse.kura.protocol.modbus.ModbusProtocolDeviceService;
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
import org.osgi.service.metatype.annotations.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSON;
import com.yofc.iot.apps.modbus.ModbusDriver.Config;

@Designate(ocd = Config.class, factory = true)
@Component(enabled = true, configurationPolicy = ConfigurationPolicy.REQUIRE, property = {
        "service.pid=com.yofc.iot.apps.modbus.ModbusDriver" })
public class ModbusDriver implements Driver, ConfigurableComponent {

    private static final Logger logger = LoggerFactory.getLogger(ModbusDriver.class);
    private Properties modbusProperties = null;
    private Properties preModbusProperties = null;
    @Reference(unbind = "unbindModbusProtocolDeviceService")
    private ModbusProtocolDeviceService protocolDevice;

    public void unbindModbusProtocolDeviceService(final ModbusProtocolDeviceService protocolDevice) {
        if (this.protocolDevice == protocolDevice) {
            this.protocolDevice = null;
        }
    }

    @Activate
    protected synchronized void activate(final Config properties) {
        logger.info("Activating Modbus Driver...");
        this.modbusProperties = this.getModbusProperties(properties);
        this.preModbusProperties = this.modbusProperties;
        this.modbusProperties = this.getModbusProperties(properties);
        logger.info("Activating Modbus Driver... Done");
    }

    @Modified
    public synchronized void updated(final Config properties) throws ConnectionException {
        logger.info("Updating Modbus Driver...");
        disconnect();
        this.modbusProperties = this.getModbusProperties(properties);
        logger.info("Updating Modbus Driver... Done");
    }

    @Deactivate
    public synchronized void deactivate() {
        logger.debug("Deactivating Modbus Driver...");
        try {
            disconnect();
        } catch (final ConnectionException e) {
            logger.error("Error while disconnecting...", e);
            return;
        }
        logger.debug("Deactivating Modbus Driver.....Done");
    }

    @Override
    public synchronized void connect() throws ConnectionException {
        try {
            if (this.protocolDevice == null)
                throw new ConnectionException("ModBusProtocolDevice Service not present");
            if (!this.modbusProperties.equals(this.preModbusProperties)) {
                logger.info("configuration changed, disconnecting...");
                disconnect();
                this.protocolDevice.configureConnection(this.modbusProperties);
                this.preModbusProperties = this.modbusProperties;
                logger.info("configuration changed, disconnecting...Done");
            }
            if (!isConnected()) {
                logger.debug("Connecting to Modbus...");
                if (this.protocolDevice.getConnectStatus() == KuraConnectionStatus.NEVERCONNECTED)
                    this.protocolDevice.configureConnection(this.modbusProperties);
                this.protocolDevice.connect();
                logger.debug("Connecting to Modbus... Done");
            }
        } catch (Exception e) {
            throw new ConnectionException("Modbus Connection failed, unexpected exception", e);
        }
    }

    @Override
    public synchronized void disconnect() throws ConnectionException {
        if (this.protocolDevice != null) {
            logger.debug("Disconnecting from Modbus...");
            try {
                this.protocolDevice.disconnect();
            } catch (Exception e) {
                throw new ConnectionException(e);
            }finally {
                this.protocolDevice=null;
            }
            logger.debug("Disconnecting from Modbus... Done");
        }

    }

    @Override
    public ChannelDescriptor getChannelDescriptor() {
        return new ModbusChannelDescriptor();
    }

    private boolean isConnected() throws ConnectionException {
        if (this.protocolDevice == null)
            throw new ConnectionException("ModBusProtocolDevice Service not present");
        return this.protocolDevice.getConnectStatus() == KuraConnectionStatus.CONNECTED;
    }

    @Override
    public synchronized void read(List<ChannelRecord> records) throws ConnectionException {
        if (!isConnected()) {
            connect();
        }
        for (ChannelRecord record : records) {
            Integer dataAddress = 0;
            Integer count = 1;
            String registerType;
            Integer slaveAddr = 1;
            try {
                dataAddress = getIntProperty(record, ModbusChannelDescriptor.DATA_ADDRESS, "Modbus Tag setting error");
                count = getIntProperty(record, ModbusChannelDescriptor.DATA_TAG_READ_LENGTH,
                        "Modbus Tag setting error");
                registerType = getStringProperty(record, ModbusChannelDescriptor.REGISTER_TYPE,
                        "Modbus Tag setting error");
                slaveAddr = getIntProperty(record, ModbusChannelDescriptor.SLAVE_ADDR, "Modbus Tag setting error");
            } catch (KuraException e) {
                record.setChannelStatus(new ChannelStatus(ChannelFlag.FAILURE, e.getMessage(), e));
                record.setTimestamp(System.currentTimeMillis());
                continue;
            }
            try {
                int[] readata = null;
                if (RegisterType.INPUT.name().equals(registerType))
                    readata = this.protocolDevice.readInputRegisters(slaveAddr, dataAddress, count);
                else if (RegisterType.HODING.name().equals(registerType))
                    readata = this.protocolDevice.readHoldingRegisters(slaveAddr, dataAddress, count);
                else if (RegisterType.DISCRETE.name().equals(registerType)) {
                    boolean[] disReadata = this.protocolDevice.readDiscreteInputs(slaveAddr, dataAddress, count);
                    fillRecord(disReadata, record);
                } else if (RegisterType.COILS.name().equals(registerType)) {
                    boolean[] coilsReadata = this.protocolDevice.readCoils(slaveAddr, dataAddress, count);
                    fillRecord(coilsReadata, record);
                } else
                    throw new ConnectionException("ModBusProtocolDevice Service not present");
                fillRecord(readata, record);
            } catch (Exception e) {
                logger.error("Modbus read error", e);
                record.setChannelStatus(new ChannelStatus(ChannelFlag.FAILURE, e.getMessage(), e));
                record.setTimestamp(System.currentTimeMillis());
            }

        }

    }

    public static void fillRecord(final Object value, final ChannelRecord record) {
        record.setTimestamp(System.currentTimeMillis());
        if (value == null) {
            record.setChannelStatus(new ChannelStatus(FAILURE, "Operation Result cannot be null", null));
            return;
        }
        try {
            if (value instanceof int[]) {
                int[] result = (int[]) value;
                if (result.length == 1)
                    record.setValue(map(result[0], record.getValueType()));
                else
                    record.setValue(TypedValues.newStringValue(JSON.toJSONString(value)));
            } else if (value instanceof boolean[]) {
                boolean[] result = (boolean[]) value;
                if (result.length == 1)
                    record.setValue(map(result[0] ? 1 : 0, record.getValueType()));
                else
                    record.setValue(TypedValues.newStringValue(JSON.toJSONString(value)));
            } else {
                record.setChannelStatus(new ChannelStatus(FAILURE, "Operation Result type error", null));
                return;
            }
            record.setChannelStatus(new ChannelStatus(ChannelFlag.SUCCESS));
        } catch (Exception e) {
            record.setChannelStatus(
                    new ChannelStatus(FAILURE, "Error while converting the retrieved value to the defined typed", e));
        }

    }

    private static TypedValue<?> map(final int value, final DataType targetType) throws Exception {
        Number valueObj = value;
        switch (targetType) {
        case LONG:
            return TypedValues.newLongValue(valueObj.longValue());
        case FLOAT:
            return TypedValues.newFloatValue(valueObj.floatValue());
        case DOUBLE:
            return TypedValues.newDoubleValue(valueObj.doubleValue());
        case INTEGER:
            return TypedValues.newIntegerValue(value);
        case BOOLEAN:
            return TypedValues.newBooleanValue(valueObj.intValue() != 0);
        case STRING:
            return TypedValues.newStringValue(valueObj == null ? "" : valueObj.toString());
        case BYTE_ARRAY:
            return TypedValues.newByteArrayValue(intToByteArray(value));
        default:
            throw new IllegalArgumentException();
        }
    }

    public static final byte[] intToByteArray(int value) {
        return new byte[] { (byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value };
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
    public synchronized void write(List<ChannelRecord> records) throws ConnectionException {
        if (!isConnected()) {
            connect();
        }

        for (ChannelRecord record : records) {
            String data = String.valueOf(record.getValue().getValue());
            if (data == null)
                throw new ConnectionException("Data Error");
            Integer dataAddress = 0;
            String registerType;
            Integer slaveAddr = 1;
            try {
                dataAddress = getIntProperty(record, ModbusChannelDescriptor.DATA_ADDRESS, "Modbus Tag setting error");
                registerType = getStringProperty(record, ModbusChannelDescriptor.REGISTER_TYPE,
                        "Modbus Tag setting error");
            } catch (KuraException e) {
                record.setChannelStatus(new ChannelStatus(ChannelFlag.FAILURE, e.getMessage(), e));
                record.setTimestamp(System.currentTimeMillis());
                continue;
            }
            record.setTimestamp(System.currentTimeMillis());
            try {
                List<Integer> readataList = JSON.parseArray(data, Integer.class);
                int[] readata = readataList.stream().flatMapToInt(num -> IntStream.of(num.intValue())).toArray();
                if (RegisterType.INPUT.name().equals(registerType))
                    this.protocolDevice.writeMultipleRegister(slaveAddr, dataAddress, readata);
                else if (RegisterType.HODING.name().equals(registerType))
                    this.protocolDevice.writeMultipleRegister(slaveAddr, dataAddress, readata);
                else
                    throw new ConnectionException("ModBusProtocolDevice Service not present");
                fillRecord(readata, record);

                record.setChannelStatus(new ChannelStatus(ChannelFlag.SUCCESS));
            } catch (Exception e) {

                record.setChannelStatus(new ChannelStatus(ChannelFlag.FAILURE, e.getMessage(), e));

                throw new ConnectionException(e);

            }

        }
    }

    @Override
    public PreparedRead prepareRead(List<ChannelRecord> records) {
        return new ModbusPreparedRead(records);
    }

    private class ModbusPreparedRead implements PreparedRead {

        private final List<ChannelRecord> channelRecords;

        public ModbusPreparedRead(final List<ChannelRecord> records) {
            this.channelRecords = records;
        }

        @Override
        public List<ChannelRecord> execute() throws ConnectionException {
            try {
                ModbusDriver.this.read(this.channelRecords);
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

    private static String getStringProperty(ChannelRecord record, String propertyName, String failureMessage)
            throws KuraException {
        try {
            return String.valueOf(record.getChannelConfig().get(propertyName).toString());
        } catch (Exception e) {
            throw new KuraException(KuraErrorCode.CONFIGURATION_ERROR, failureMessage);
        }
    }

    private static int getIntProperty(ChannelRecord record, String propertyName, String failureMessage)
            throws KuraException {
        try {
            return Integer.valueOf(record.getChannelConfig().get(propertyName).toString());
        } catch (Exception e) {
            throw new KuraException(KuraErrorCode.CONFIGURATION_ERROR, failureMessage);
        }
    }

    private Properties getModbusProperties(Config currentOptions) {

        if (currentOptions == null) {
            return null;
        }
        Properties prop = new Properties();
        String modbusProtocol = currentOptions.protocol();
        prop.setProperty("connectionType", modbusProtocol);
        boolean isTCP = "TCP-RTU".equals(modbusProtocol) || "TCP/IP".equals(modbusProtocol);
        if (isTCP) {
            int iport = currentOptions.tcpPort();
            if (iport != 0) {
                prop.setProperty("ethport", String.valueOf(iport));
            }
            prop.setProperty("ipAddress", currentOptions.ipAddress());
        } else {
            String portName = currentOptions.serialPort();
            String baudRate = currentOptions.baudRate();
            String bitsPerWord = currentOptions.stopBits();
            String stopBits = currentOptions.stopBits();
            String parity = currentOptions.parity();

            if (portName == null) {
                return null;
            }
            if (baudRate == null) {
                baudRate = "9600";
            }
            if (stopBits == null) {
                stopBits = "1";
            }
            if (parity == null) {
                parity = "0";
            }
            if (bitsPerWord == null) {
                bitsPerWord = "8";
            }

            prop.setProperty("port", portName);
            prop.setProperty("exclusive", "false");
            prop.setProperty("baudRate", baudRate);
            prop.setProperty("stopBits", stopBits);
            prop.setProperty("parity", parity);
            prop.setProperty("bitsPerWord", bitsPerWord);
        }
        prop.setProperty("mode", "0");
        prop.setProperty("transmissionMode", "RTU");
        prop.setProperty("respTimeout", "1000");
        return prop;
    }

    @ObjectClassDefinition(id = "com.yofc.iot.apps.modbus.ModbusDriver", name = "%name", description = "%description", localization = "OSGI-INF/l10n/com.yofc.iot.apps.modbus.Config")
    @interface Config {

        @AttributeDefinition(name = "%protocol", description = "%protocolDesc", options = {
                @Option(label = "RS-232", value = "RS232"), @Option(label = "TCP-RTU", value = "TCP-RTU"),
                @Option(label = "TCP/IP", value = "TCP/IP") })
        String protocol() default "TCP/IP";

        @AttributeDefinition(name = "%serialPort", description = "%serialPortDesc")
        String serialPort() default "/dev/ttyUSB0";

        @AttributeDefinition(name = "%baudRate", description = "%baudRateDesc", options = {
                @Option(label = "110", value = "110"), @Option(label = "300", value = "300"),
                @Option(label = "600", value = "600"), @Option(label = "1200", value = "1200"),
                @Option(label = "2400", value = "2400"), @Option(label = "4800", value = "4800"),
                @Option(label = "9600", value = "9600"), @Option(label = "14400", value = "14400"),
                @Option(label = "19200", value = "19200"), @Option(label = "28800", value = "28800"),
                @Option(label = "38400", value = "38400"), @Option(label = "56000", value = "56000"),
                @Option(label = "57600", value = "57600"), @Option(label = "115200", value = "115200"),
                @Option(label = "128000", value = "128000"), @Option(label = "153600", value = "153600"),
                @Option(label = "230400", value = "230400"), @Option(label = "256000", value = "256000"),
                @Option(label = "460800", value = "460800"), @Option(label = "921600", value = "921600") })

        String baudRate() default "115200";

        @AttributeDefinition(name = "%flowControl", description = "%flowControlDesc")
        int flowControl() default 0;

        @AttributeDefinition(name = "%parity", description = "%parityDesc", options = {
                @Option(label = "None", value = "0"), @Option(label = "Odd", value = "1"),
                @Option(label = "Even", value = "2"), @Option(label = "Mark", value = "3"),
                @Option(label = "Space", value = "4") })
        String parity() default "0";

        @AttributeDefinition(name = "%bitsPerWord", description = "%bitsPerWordDesc", options = {
                @Option(label = "5", value = "5"), @Option(label = "6", value = "6"), @Option(label = "7", value = "7"),
                @Option(label = "8", value = "8") })
        String bitsPerWord() default "8";

        @AttributeDefinition(name = "%stopBits", description = "%stopBitsDesc", options = {
                @Option(label = "1", value = "1"), @Option(label = "2", value = "2"),
                @Option(label = "1.5", value = "3") })
        String stopBits() default "1";

        @AttributeDefinition(name = "%ipAddress", description = "%ipAddressDesc")
        String ipAddress() default "localhost";

        @AttributeDefinition(name = "%tcpPort", description = "%tcpPortDesc")
        int tcpPort() default 502;

    }

}
