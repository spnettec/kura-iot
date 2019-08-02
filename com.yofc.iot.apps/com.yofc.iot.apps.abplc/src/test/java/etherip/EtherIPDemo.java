/*******************************************************************************
 * Copyright (c) 2012 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package etherip;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;

import etherip.data.CipException;
import etherip.types.CIPData;
import etherip.types.CIPData.Type;

public class EtherIPDemo {

    @Before
    public void setup() {

    }

    @Test
    public void testConnectTcp() throws Exception {
        try (EtherNetIP plc = new EtherNetIP("10.166.11.181", 0);) {
            plc.connectTcp();

            System.out.println("\n*\n* Connected:\n*\n");
            System.out.println(plc.getIdentity());
        } catch (final CipException e) {
            // It is possible to ask some individual field of the ExceptionCip, but these are included in the
            // getMessage().
            // System.err.println(e.getStatusCode());
            // System.err.println(e.getStatusName());
            // System.err.println(e.getStatusDescription());
            System.err.println(e.getMessage());
            System.err.println("Failed with CipException");
            fail("Failed with CipException");
        }
    }

    @Test
    public void testFloat() throws Exception {
        try (EtherNetIP plc = new EtherNetIP("10.166.11.181", 0)) {
            plc.connectTcp();

            final String tag = "HSTOCK_ROT_MOTOR_SPD_ACT";

            System.out.println("\n*\n* float '" + tag + "':\n*\n");
            CIPData value = plc.readTag(tag);
            System.out.println(value);
            value.set(0, 48.12);

            // assertThat(value, not(nullValue()));
            // value.set(1, 47.11);
            // plc.writeTag(tag, 0, value);

            // value = plc.readTag(tag, (short) 2);
            // assertThat(value, not(nullValue()));
            // System.out.println("Changed to " + value);
            // assertEquals(47.11, value.getNumber(1).doubleValue(), 0.01);

            // value.set(1, 48.12);
            plc.writeTag(tag, value);

            value = plc.readTag(tag);
            System.out.println(value);
            // assertThat(value, not(nullValue()));
            // System.out.println("Changed to " + value);
            // assertEquals(48.12, value.getNumber(0).doubleValue(), 0.01);
        }
    }

    @Test
    public void testInt() throws Exception {
        try (EtherNetIP plc = new EtherNetIP("10.166.11.181", 0);) {
            plc.connectTcp();

            final String tag = "FURNACE_MOTOR_MODE_SELECT_HMI";

            System.out.println("\n*\n* int '" + tag + "':\n*\n");
            CIPData value = plc.readTag(tag);

            System.out.println(value.getNumber(0).intValue());
            assertThat(value, not(nullValue()));
            value.set(0, 42);
            plc.writeTag(tag, value);

            value = plc.readTag(tag);
            assertThat(value, not(nullValue()));
            System.out.println("Changed to " + value);
            assertThat(value.getNumber(0).intValue(), equalTo(42));

            value.set(0, 47);
            plc.writeTag(tag, value);

            value = plc.readTag(tag);
            assertThat(value, not(nullValue()));
            System.out.println("Changed to " + value);
            assertThat(value.getNumber(0).intValue(), equalTo(47));
        }
    }

    @Test
    public void testBool() throws Exception {
        try (EtherNetIP plc = new EtherNetIP("10.166.11.181", 0)) {
            plc.connectTcp();

            final String tag = "ALAM_RESET";
            System.out.println("\n*\n* bool '" + tag + "':\n*\n");

            CIPData value = plc.readTag(tag);
            System.out.println("Original Value: " + value);
            System.out.flush();
            System.err.flush();

            value = new CIPData(Type.BOOL, 1);

            value.set(0, 1);
            plc.writeTag(tag, value);
            value = plc.readTag(tag);
            System.out.println("Wrote 1: " + value);
            assertThat(value, not(nullValue()));
            assertThat(value.getNumber(0).intValue(), not(equalTo(0)));

            value.set(0, 0);
            plc.writeTag(tag, value);
            value = plc.readTag(tag);
            System.out.println("Wrote 0: " + value);
            assertThat(value, not(nullValue()));
            assertThat(value.getNumber(0).intValue(), equalTo(0));

            value.set(0, 255);
            plc.writeTags(new String[] { tag }, new CIPData[] { value });
            value = plc.readTag(tag);
            System.out.println("Wrote 255: " + value);
            assertThat(value, not(nullValue()));
            assertThat(value.getNumber(0).intValue(), not(equalTo(0)));
        }
    }

    @Test
    public void testString() throws Exception {
        try (EtherNetIP plc = new EtherNetIP("10.166.11.181", 0);) {
            plc.connectTcp();

            final String tag = "SYSTEM_BAR_NO";

            System.out.println("\n*\n* string '" + tag + "':\n*\n");
            CIPData value = plc.readTag(tag);
            System.out.println(value);
            assertThat(value, not(nullValue()));
            assertThat(value.isNumeric(), equalTo(false));
            String new_value = value.getString();
            if (new_value.equals("test"))
                new_value = "Testing!";
            else
                new_value = "test";
            System.out.println("Writing '" + new_value + "'");
            value.setString(new_value);
            plc.writeTag(tag, value);

            value = plc.readTag(tag);
            System.out.println(value);
            assertThat(value.getString(), equalTo(new_value));
        }
    }

    @Test
    public void testMultiRead() throws Exception {
        try (EtherNetIP plc = new EtherNetIP("10.166.11.181", 0);) {
            plc.connectTcp();

            System.out.println("\n*\n* Multi read:\n*\n");
            final String[] tags = new String[] { "ALARM1", "ALARM2", "ALARM3", "SYSTEM_BAR_NO" };
            final CIPData[] results = plc.readTags(tags);
            assertThat(results, not(nullValue()));
            assertThat(results.length, equalTo(tags.length));

            for (int i = 0; i < results.length; ++i) {
                System.out.println(tags[i] + " = " + results[i]);
            }
        }
    }
}
