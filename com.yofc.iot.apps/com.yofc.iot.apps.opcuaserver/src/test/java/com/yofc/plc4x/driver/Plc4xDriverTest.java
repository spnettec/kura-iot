package com.yofc.plc4x.driver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.apache.plc4x.java.DefaultPlcDriverManager;
import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.PlcConnectionManager;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.eclipse.kura.channel.ChannelFlag;
import org.eclipse.kura.channel.ChannelRecord;
import org.eclipse.kura.driver.PreparedRead;
import org.eclipse.kura.type.DataType;
import org.eclipse.kura.type.TypedValues;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Plc4xDriverTest {

    @TempDir
    Path tempDir;

    @Test
    void readMapsFileBackedSimulatedValue() throws Exception {
        String connectionString = connectionString();
        seed(connectionString, "FILE/ReadValue:DINT", 42);

        Plc4xDriver driver = driver(connectionString);
        ChannelRecord record = readRecord("read-value", DataType.INTEGER, "readTag", "FILE/ReadValue:DINT");

        driver.read(List.of(record));

        assertEquals(ChannelFlag.SUCCESS, record.getChannelStatus().getChannelFlag());
        assertEquals(42, record.getValue().getValue());
    }

    @Test
    void readMarksMissingChannelConfigAsFailure() throws Exception {
        Plc4xDriver driver = driver(connectionString());
        ChannelRecord record = ChannelRecord.createReadRecord("missing-config", DataType.INTEGER);

        driver.read(List.of(record));

        assertEquals(ChannelFlag.FAILURE, record.getChannelStatus().getChannelFlag());
    }

    @Test
    void writeStoresFileBackedSimulatedValue() throws Exception {
        String connectionString = connectionString();
        Plc4xDriver driver = driver(connectionString);
        ChannelRecord record = writeRecord("write-value", "writeTag", "FILE/WriteValue:DINT", 77);

        driver.write(List.of(record));

        assertEquals(ChannelFlag.SUCCESS, record.getChannelStatus().getChannelFlag());
        assertEquals(77, readDirect(connectionString, "FILE/WriteValue:DINT").getInteger());
    }

    @Test
    void writeMarksNullValueAsFailure() throws Exception {
        Plc4xDriver driver = driver(connectionString());
        ChannelRecord record = readRecord("null-write-value", DataType.INTEGER, "nullWriteTag", "FILE/NullValue:DINT");

        driver.write(List.of(record));

        assertEquals(ChannelFlag.FAILURE, record.getChannelStatus().getChannelFlag());
    }

    @Test
    void preparedReadUsesCachedChannelMappingAndCloses() throws Exception {
        String connectionString = connectionString();
        seed(connectionString, "FILE/PreparedValue:DINT", 100);

        Plc4xDriver driver = driver(connectionString);
        ChannelRecord record = readRecord("prepared-value", DataType.INTEGER, "preparedTag", "FILE/PreparedValue:DINT");
        PreparedRead preparedRead = driver.prepareRead(List.of(record));

        List<ChannelRecord> results = preparedRead.execute();

        assertEquals(record, results.get(0));
        assertEquals(ChannelFlag.SUCCESS, record.getChannelStatus().getChannelFlag());
        assertEquals(100, record.getValue().getValue());

        preparedRead.close();
        assertThrows(Exception.class, preparedRead::execute);
    }

    @Test
    void duplicateTagNamesOnlyKeepFirstRecordReadable() throws Exception {
        String connectionString = connectionString();
        seed(connectionString, "FILE/DuplicateValue:DINT", 12);

        Plc4xDriver driver = driver(connectionString);
        ChannelRecord first = readRecord("duplicate-one", DataType.INTEGER, "sameTag", "FILE/DuplicateValue:DINT");
        ChannelRecord second = readRecord("duplicate-two", DataType.INTEGER, "sameTag", "FILE/DuplicateValue:DINT");

        driver.read(List.of(first, second));

        assertEquals(ChannelFlag.SUCCESS, first.getChannelStatus().getChannelFlag());
        assertEquals(ChannelFlag.FAILURE, second.getChannelStatus().getChannelFlag());
    }

    private String connectionString() throws Exception {
        Files.createDirectories(tempDir);
        return "simulated:127.0.0.1?file=" + tempDir.resolve("plc4x-driver-test.mv");
    }

    private static Plc4xDriver driver(String connectionString) throws Exception {
        Plc4xDriver driver = new Plc4xDriver();
        injectConnectionManager(driver, new DefaultPlcDriverManager());
        driver.activate(null, config(connectionString));
        return driver;
    }

    private static void injectConnectionManager(Plc4xDriver driver, PlcConnectionManager connectionManager)
            throws Exception {
        Field field = Plc4xDriver.class.getDeclaredField("plcConnectionManager");
        field.setAccessible(true);
        field.set(driver, connectionManager);
    }

    private static Plc4xDriver.Config config(String connectionString) {
        return new Plc4xDriver.Config() {

            @Override
            public String connectString() {
                return connectionString;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return Plc4xDriver.Config.class;
            }
        };
    }

    private static ChannelRecord readRecord(String channelName, DataType valueType, String tagName, String tagAddr) {
        ChannelRecord record = ChannelRecord.createReadRecord(channelName, valueType);
        record.setChannelConfig(Map.of("+name", tagName, Plc4xChannelDescriptor.DATA_TAG_ADDR_ID, tagAddr));
        return record;
    }

    private static ChannelRecord writeRecord(String channelName, String tagName, String tagAddr, int value) {
        ChannelRecord record = ChannelRecord.createWriteRecord(channelName, TypedValues.newIntegerValue(value));
        record.setChannelConfig(Map.of("+name", tagName, Plc4xChannelDescriptor.DATA_TAG_ADDR_ID, tagAddr));
        return record;
    }

    private static void seed(String connectionString, String tagAddr, int value) throws Exception {
        try (PlcConnection connection = new DefaultPlcDriverManager().getConnection(connectionString)) {
            connection.writeRequestBuilder().addTagAddress("seed", tagAddr, value).build().execute().get();
        }
    }

    private static org.apache.plc4x.java.api.value.PlcValue readDirect(String connectionString, String tagAddr)
            throws Exception {
        try (PlcConnection connection = new DefaultPlcDriverManager().getConnection(connectionString)) {
            PlcReadResponse response = connection.readRequestBuilder().addTagAddress("direct", tagAddr).build()
                    .execute().get();
            assertNotNull(response.getPlcValue("direct"));
            assertFalse(response.getPlcValue("direct").isNull());
            return response.getPlcValue("direct");
        }
    }
}
