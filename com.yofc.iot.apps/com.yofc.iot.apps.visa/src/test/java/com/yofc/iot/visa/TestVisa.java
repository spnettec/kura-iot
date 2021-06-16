package com.yofc.iot.visa;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import JISA.Addresses.TCPIPAddress;
import JISA.VISA.VISA;
import JISA.VISA.VISADevice;
import io.vertx.junit5.VertxExtension;

@ExtendWith(VertxExtension.class)
public class TestVisa {

    private static final Logger logger = LoggerFactory.getLogger(TestVisa.class);

    @Test
    public void test() throws IOException {
        VISA.init();
        VISADevice device;
        device = new VISADevice(new TCPIPAddress(2, "127.0.0.1"));
        device.setTimeout(500);
        device.write("IND%s%d", "asa", 1);
    }
}
