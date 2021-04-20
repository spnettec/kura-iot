package org.com.yofc.iot.apps.s7plc;

import com.yofc.iot.apps.plc.api.exceptions.PlcConnectionException;
import com.yofc.iot.apps.plc.api.messages.PlcReadRequest;
import com.yofc.iot.apps.plc.api.messages.PlcReadResponse;
import com.yofc.iot.apps.plc.protocols.s7.driver.S7PlcDriver;
import com.yofc.iot.apps.plc.protocols.s7.driver.connection.S7PlcConnection;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertNotNull;

/** Unit test for simple App. */
public class AppTest {

  Logger logger = LoggerFactory.getLogger(AppTest.class);

  @Before
  public void setup() {}

  @Test
  public void testConnectTcp()
      throws PlcConnectionException, InterruptedException, ExecutionException {
    S7PlcConnection s7Connection =
        (S7PlcConnection) new S7PlcDriver().connect("s7://10.150.34.2/0/2");
    s7Connection.connect();
    PlcReadRequest request =
        s7Connection.readRequestBuilder().addItem("test", "%DB4.DBD96.0:REAL").build();
    CompletableFuture<PlcReadResponse> responseFuture = s7Connection.read(request);
    PlcReadResponse response = responseFuture.get();
    logger.info("vlue:{}", response.getDouble("test"));
    assertNotNull(response);
  }
}
