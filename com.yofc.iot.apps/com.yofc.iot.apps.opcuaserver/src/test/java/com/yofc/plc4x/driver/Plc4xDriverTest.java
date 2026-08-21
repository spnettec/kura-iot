package com.yofc.plc4x.driver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.plc4x.java.DefaultPlcDriverManager;
import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.PlcConnectionManager;
import org.apache.plc4x.java.api.PlcDriverManager;
import org.apache.plc4x.java.api.authentication.PlcAuthentication;
import org.apache.plc4x.java.api.exceptions.PlcConnectionException;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.apache.plc4x.java.spi.values.PlcCHAR;
import org.apache.plc4x.java.utils.cache.PlcConnectionCache;
import org.apache.plc4x.java.spi.values.PlcDINT;
import org.apache.plc4x.java.spi.values.PlcList;
import org.apache.plc4x.java.spi.values.PlcSTRING;
import org.eclipse.kura.channel.ChannelFlag;
import org.eclipse.kura.channel.ChannelRecord;
import org.eclipse.kura.driver.Driver.ConnectionException;
import org.eclipse.kura.driver.PreparedRead;
import org.eclipse.kura.type.DataType;
import org.eclipse.kura.type.TypedValue;
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
    void readUsesAssetRequestTimeoutFromChannelConfig() throws Exception {
        String connectionString = connectionString();
        seed(connectionString, "FILE/ReadValueWithTimeout:DINT", 42);

        Plc4xDriver driver = driver(connectionString);
        ChannelRecord record = readRecord("read-value-timeout", DataType.INTEGER, "readTimeoutTag",
                "FILE/ReadValueWithTimeout:DINT");
        record.getChannelConfig().put("request.timeout", 1);

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

    @Test
    void readPropagatesConnectionFailure() throws Exception {
        Plc4xDriver driver = driver("s7://192.0.2.1");
        injectConnectionManager(driver, new FailingConnectionManager());
        ChannelRecord record = readRecord("read-value", DataType.INTEGER, "readTag", "%DB1:0:DINT");

        assertThrows(ConnectionException.class, () -> driver.read(List.of(record)));
    }

    @Test
    void mapsJavaStringToStringArray() throws Exception {
        assertArrayEquals(new String[] { "abc" }, (String[]) map("abc", DataType.STRINGS).getValue());
    }

    @Test
    void mapsJavaObjectArrayToStringArray() throws Exception {
        assertArrayEquals(new String[] { "a", "b" }, (String[]) map(new String[] { "a", "b" }, DataType.STRINGS).getValue());
    }

    @Test
    void mapsPlcListToStringArray() throws Exception {
        PlcList value = new PlcList(List.of(PlcSTRING.of("hello"), PlcCHAR.of("X")));

        assertArrayEquals(new String[] { "hello", "X" }, (String[]) map(value, DataType.STRINGS).getValue());
    }

    @Test
    void mapsPrimitiveByteArrayWithoutPlcValueWrapping() throws Exception {
        assertArrayEquals(new byte[] { 1, 2, -1 }, (byte[]) map(new byte[] { 1, 2, -1 }, DataType.BYTE_ARRAY).getValue());
    }

    @Test
    void mapsNumericPlcListToIntegerArray() throws Exception {
        PlcList value = new PlcList(List.of(PlcDINT.of(1), PlcDINT.of(2), PlcDINT.of(3)));

        assertArrayEquals(new Integer[] { 1, 2, 3 }, (Integer[]) map(value, DataType.INTEGERS).getValue());
    }

    @Test
    void mapsTemporalValuesToLongs() throws Exception {
        LocalDate date = LocalDate.of(2026, 6, 7);
        LocalTime time = LocalTime.of(1, 2, 3, 4);
        LocalDateTime dateTime = LocalDateTime.of(2026, 6, 7, 1, 2, 3);
        Duration duration = Duration.ofMillis(123);

        assertEquals(date.toEpochDay(), map(date, DataType.LONG).getValue());
        assertEquals(time.toNanoOfDay(), map(time, DataType.LONG).getValue());
        assertEquals(dateTime.toInstant(ZoneOffset.UTC).toEpochMilli(), map(dateTime, DataType.LONG).getValue());
        assertEquals(duration.toNanos(), map(duration, DataType.LONG).getValue());
        assertArrayEquals(new Long[] { date.toEpochDay(), time.toNanoOfDay(), duration.toNanos() },
                (Long[]) map(Arrays.asList(date, time, duration), DataType.LONGS).getValue());
    }

    private static TypedValue<?> map(Object value, DataType targetType) throws Exception {
        Method method = Plc4xDriver.class.getDeclaredMethod("map", Object.class, DataType.class);
        method.setAccessible(true);
        return (TypedValue<?>) method.invoke(null, value, targetType);
    }

    private String connectionString() throws Exception {
        Files.createDirectories(tempDir);
        return "simulated:127.0.0.1?file=" + tempDir.resolve("plc4x-driver-test.mv");
    }

    private static Plc4xDriver driver(String connectionString) throws Exception {
        Plc4xDriver driver = new Plc4xDriver();
        injectConnectionManager(driver,
                PlcConnectionCache.getBuilder().withConnectionFactory(new DefaultPlcDriverManager()).build());
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

    private static final class FailingConnectionManager implements PlcConnectionManager {

        @Override
        public PlcConnection getConnection(String url) throws PlcConnectionException {
            throw new PlcConnectionException("connection failed");
        }

        @Override
        public PlcConnection getConnection(String url, PlcAuthentication authentication) throws PlcConnectionException {
            throw new PlcConnectionException("connection failed");
        }

        @Override
        public PlcDriverManager getDriverManager() {
            return null;
        }

        @Override
        public void close() throws PlcConnectionException {
            // nothing to close
        }
    }
}
