package org.com.yofc.iot.apps.s7plc;

import org.apache.plc4x.java.PlcDriverManager;
import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.exceptions.PlcConnectionException;
import org.apache.plc4x.java.api.messages.PlcReadRequest;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Unit test for simple App. */
public class AppTest {

    Logger logger = LoggerFactory.getLogger(AppTest.class);

    @Before
    public void setup() {
    }

    @Test
    public void testConnectTcp() throws PlcConnectionException, Exception {
        try (PlcConnection plcConnection = new PlcDriverManager().getConnection("s7://10.150.34.2/0/2")) {
            final PlcReadRequest readRequest = plcConnection.readRequestBuilder().addItem("test", "%DB4.DBD96.0:REAL")
                    .build();
            final PlcReadResponse readResponse = readRequest.execute().get();
            logger.info("value:{}", readResponse.getAsPlcValue().getFloat());
        }

    }
}
