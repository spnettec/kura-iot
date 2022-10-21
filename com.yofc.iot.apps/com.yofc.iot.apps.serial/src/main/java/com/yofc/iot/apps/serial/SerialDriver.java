package com.yofc.iot.apps.serial;

import com.yofc.iot.apps.serial.SerialDriver.Config;
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
import org.eclipse.kura.type.TypedValues;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;
import static org.eclipse.kura.channel.ChannelFlag.FAILURE;

@Designate(ocd = Config.class, factory = true)
@Component(
    enabled = true,
    configurationPolicy = ConfigurationPolicy.REQUIRE,
    property = {"service.pid=com.yofc.iot.apps.serial.SerialDriver"})
public class SerialDriver implements Driver, ConfigurableComponent {

  private static final Logger logger = LoggerFactory.getLogger(SerialDriver.class);
  private final AtomicReference<Config> options = new AtomicReference<>();
  private SerialClientState state = new SerialClientState(null);

  public static void fillRecord(final String value, final ChannelRecord record) {
    record.setTimestamp(System.currentTimeMillis());

    if (value == null) {
      record.setChannelStatus(new ChannelStatus(FAILURE, "Operation Result cannot be null", null));
      return;
    }
    record.setTimestamp(System.currentTimeMillis());
    try {
      record.setValue(TypedValues.newStringValue(value == null ? "" : value));
      record.setChannelStatus(new ChannelStatus(ChannelFlag.SUCCESS));
    } catch (Exception e) {
      record.setChannelStatus(
          new ChannelStatus(
              FAILURE, "Error while converting the retrieved value to the defined typed", e));
    }
  }

  private static String getStringProperty(
      ChannelRecord record, String propertyName, String failureMessage) throws KuraException {
    try {
      return String.valueOf(record.getChannelConfig().get(propertyName).toString());
    } catch (Exception e) {
      throw new KuraException(KuraErrorCode.CONFIGURATION_ERROR, failureMessage);
    }
  }

  private static int getIntProperty(
      ChannelRecord record, String propertyName, String failureMessage) throws KuraException {
    try {
      return Integer.parseInt(record.getChannelConfig().get(propertyName).toString());
    } catch (Exception e) {
      throw new KuraException(KuraErrorCode.CONFIGURATION_ERROR, failureMessage);
    }
  }

  @Activate
  protected synchronized void activate(final Config properties) {
    logger.info("Activating Serial Driver...");
    extractProperties(properties);
    logger.info("Activating Serial Driver... Done");
  }

  private void extractProperties(final Config properties) {
    requireNonNull(properties, "Properties cannot be null");
    this.options.set(properties);
  }

  @Modified
  public synchronized void updated(final Config properties) throws ConnectionException {
    logger.info("Updating Serial Driver...");
    disconnect();
    extractProperties(properties);

    logger.info("Updating Serial Driver... Done");
  }

  @Deactivate
  public synchronized void deactivate() {
    logger.debug("Deactivating Serial Driver...");
    try {
      disconnect();
    } catch (final ConnectionException e) {
      logger.error("Error while disconnecting...", e);
      return;
    }
    logger.debug("Deactivating Serial Driver.....Done");
  }

  @Override
  public synchronized void connect() throws ConnectionException {
    try {
      final Config currentOptions = this.options.get();

      if (this.state.client == null || this.state.options != currentOptions) {
        logger.info("configuration changed, disconnecting...");
        disconnect();
        this.state = createClientState(currentOptions);
        logger.info("configuration changed, disconnecting...Done");
      }

      if (!this.state.client.isConnected()) {
        logger.debug("Connecting to Serial...");
        this.state.client.connect();
        logger.debug("Connecting to Serial... Done");
      }
    } catch (Exception e) {
      throw new ConnectionException("Serial Connection failed, unexpected exception", e);
    }
  }

  protected SerialClientState createClientState(final Config options) {
    return new SerialClientState(options);
  }

  @Override
  public synchronized void disconnect() throws ConnectionException {
    if (this.state.client != null && this.state.client.isConnected()) {
      logger.debug("Disconnecting from Serial...");
      try {
        this.state.client.close();
        state = new SerialClientState(null);
      } catch (Exception e) {
        throw new ConnectionException(e);
      }
      logger.debug("Disconnecting from Serial... Done");
    }
  }

  @Override
  public ChannelDescriptor getChannelDescriptor() {
    return new SerialChannelDescriptor();
  }

  @Override
  public void read(List<ChannelRecord> records) throws ConnectionException {
    if (this.state.client == null || !this.state.client.isConnected()) {
      connect();
    }
    for (ChannelRecord record : records) {
      String tagName;
      int tagReadLength;
      try {
        tagName = getStringProperty(record, SerialChannelDescriptor.DATA_TAG_NAME_ID, "Serial Tag setting error");
      } catch (KuraException e) {
        record.setChannelStatus(new ChannelStatus(ChannelFlag.FAILURE, e.getMessage(), e));
        record.setTimestamp(System.currentTimeMillis());
        continue;
      }
      try {
        tagReadLength = getIntProperty(record, SerialChannelDescriptor.DATA_TAG_READ_LENGTH,
                "Serial Tag setting error");
      } catch (KuraException e) {
        record.setChannelStatus(new ChannelStatus(ChannelFlag.FAILURE, e.getMessage(), e));
        record.setTimestamp(System.currentTimeMillis());
        continue;
      }
      if (tagName == null) {
        record.setChannelStatus(new ChannelStatus(ChannelFlag.FAILURE, "Serial Tag is null",
                new KuraException(KuraErrorCode.CONFIGURATION_ERROR)));
        record.setTimestamp(System.currentTimeMillis());
      } else {
        try {
          String readata = this.state.client.readTag(tagName, tagReadLength);
          fillRecord(readata, record);
        } catch (Exception e) {
          logger.error("Serial tag read error", e);
          record.setChannelStatus(new ChannelStatus(ChannelFlag.FAILURE, e.getMessage(), e));
          record.setTimestamp(System.currentTimeMillis());
        }
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
  public synchronized void write(List<ChannelRecord> records) throws ConnectionException {
    if (this.state.client == null || !this.state.client.isConnected()) {
      connect();
    }
    List<Request<WriteParams>> requests = Request.extractWriteRequests(records);
    for (Request<WriteParams> request : requests) {
      WriteParams para = request.getParameters();

      String tagName = para.getWriteTag();
      String data = para.getWriteValue();
      if (data == null) {
        throw new ConnectionException("Data Error");
      }

      ChannelRecord record = request.getRecord();
      Integer tagReadLength = null;
      try {
        tagReadLength = getIntProperty(record, SerialChannelDescriptor.DATA_TAG_READ_LENGTH,
                "Serial Tag setting error");
      } catch (KuraException e) {
        record.setChannelStatus(new ChannelStatus(ChannelFlag.FAILURE, e.getMessage(), e));
        record.setTimestamp(System.currentTimeMillis());
        continue;
      }
      record.setTimestamp(System.currentTimeMillis());
      try {
        this.state.client.writeTag(tagName, data, tagReadLength);

        record.setChannelStatus(new ChannelStatus(ChannelFlag.SUCCESS));
      } catch (Exception e) {

        record.setChannelStatus(new ChannelStatus(ChannelFlag.FAILURE, e.getMessage(), e));

        throw new ConnectionException(e);
      }
    }
  }

  @Override
  public PreparedRead prepareRead(List<ChannelRecord> records) {
    return new SerialPreparedRead(records);
  }

  @ObjectClassDefinition(
      id = "com.yofc.iot.apps.serial.SerialDriver",
      name = "%name",
      description = "%description",
      localization = "OSGI-INF/l10n/com.yofc.iot.apps.serial.Config")
  @interface Config {

    @AttributeDefinition(name = "%serialPort", description = "%serialPortDesc")
    String serialPort() default "/dev/ttyUSB0";

    @AttributeDefinition(name = "%baudRate", description = "%baudRateDesc")
    int baudRate() default 115200;

    @AttributeDefinition(name = "%flowControl", description = "%flowControlDesc")
    int flowControl() default 0;

    @AttributeDefinition(name = "%parity", description = "%parityDesc")
    int parity() default 0;

    @AttributeDefinition(name = "%stopBits", description = "%stopBitsDesc")
    int stopBits() default 8;
  }

  static final class SerialClientState {

    private final SerialDriverClient client;
    private final Config options;

    SerialClientState(final Config options) {
      this(
          options,
          options == null
              ? null
              : new SerialDriverClient(
                  options.serialPort(),
                  options.baudRate(),
                  options.flowControl(),
                  options.parity(),
                  options.stopBits()));
    }

    public SerialClientState(final Config options, final SerialDriverClient client) {
      this.options = options;
      this.client = client;
    }
  }

  private class SerialPreparedRead implements PreparedRead {

    private final List<ChannelRecord> channelRecords;

    public SerialPreparedRead(final List<ChannelRecord> records) {
      this.channelRecords = records;
    }

    @Override
    public List<ChannelRecord> execute() throws ConnectionException {
      try {
        SerialDriver.this.read(this.channelRecords);
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
}
