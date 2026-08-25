/*
 Licensed to the Apache Software Foundation (ASF) under one
 or more contributor license agreements.  See the NOTICE file
 distributed with this work for additional information
 regarding copyright ownership.  The ASF licenses this file
 to you under the Apache License, Version 2.0 (the
 "License"); you may not use this file except in compliance
 with the License.  You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing,
 software distributed under the License is distributed on an
 "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 KIND, either express or implied.  See the License for the
 specific language governing permissions and limitations
 under the License.
*/
package org.apache.plc4x.java.opcuaserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.math.BigInteger;

import org.apache.commons.io.FileUtils;
import org.apache.plc4x.java.DefaultPlcDriverManager;
import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.exceptions.PlcConnectionException;
import org.apache.plc4x.java.api.messages.PlcReadRequest;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.apache.plc4x.java.api.messages.PlcWriteRequest;
import org.apache.plc4x.java.api.messages.PlcWriteResponse;
import org.apache.plc4x.java.api.types.PlcResponseCode;
import org.apache.plc4x.java.utils.cache.PlcConnectionCache;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.yofc.opcuaserver.OPCUAServer;
import com.yofcopcuaserver.backend.Plc4xNamespace;

import io.vavr.collection.List;

/**
 */
class OpcuaPlcDriverTest {

    private static final String SIMULATED_CONNECTION_STRING = "simulated:127.0.0.1?file=~/Data/aaa";

    // Read only variables of milo example server of version 3.6
    private static String BOOL_IDENTIFIER_READ_WRITE;
    private static String BYTE_IDENTIFIER_READ_WRITE;
    private static String DOUBLE_IDENTIFIER_READ_WRITE;
    private static String FLOAT_IDENTIFIER_READ_WRITE;
    private static String INT16_IDENTIFIER_READ_WRITE;
    private static String INT32_IDENTIFIER_READ_WRITE;
    private static String INT64_IDENTIFIER_READ_WRITE;
    private static String INTEGER_IDENTIFIER_READ_WRITE;
    private static String SBYTE_IDENTIFIER_READ_WRITE;
    private static String STRING_IDENTIFIER_READ_WRITE;
    private static String UINT16_IDENTIFIER_READ_WRITE;
    private static String UINT32_IDENTIFIER_READ_WRITE;
    private static String UINT64_IDENTIFIER_READ_WRITE;
    private static String UINTEGER_IDENTIFIER_READ_WRITE;
    private static final String DOES_NOT_EXIST_IDENTIFIER_READ_WRITE = "ns=1;i=12512623";
    // Address of local milo server
    private String miloLocalAddress = "127.0.0.1:12673/iot";
    // Tcp pattern of OPC UA
    private String opcPattern = "opcua:tcp://";

    private String paramSectionDivider = "&";
    // Local Milo test server presents a self-signed certificate without a configured
    // trust anchor; upstream now fails closed (RejectingCertificateVerifier) by default
    // and refuses to silently fall back from Basic256Sha256 to an unprotected channel.
    // Explicitly ask for the unprotected channel - local testing only.
    private String tcpConnectionAddress = opcPattern + miloLocalAddress + "?insecure-certificate-verification=true&security-policy=NONE";

    private List<String> connectionStringValidSet = List.of(tcpConnectionAddress);
    private String discoveryValidParamTrue = "discovery=true";
    private String discoveryValidParamFalse = "discovery=false";
    private String discoveryCorruptedParamWrongValueNum = "discovery=1";
    private String discoveryCorruptedParamWronName = "diskovery=false";

    List<String> discoveryParamValidSet = List.of(discoveryValidParamTrue, discoveryValidParamFalse);
    List<String> discoveryParamCorruptedSet = List.of(discoveryCorruptedParamWrongValueNum,
            discoveryCorruptedParamWronName);

    private static OPCUAServer exampleServer;

    @BeforeAll
    public static void setup() throws Exception {
        FileUtils.deleteDirectory(new File("target/test-tmp"));
        seedSimulatedFileValues();
        exampleServer = new OPCUAServer("src/test/resources/config.yml", true,
                PlcConnectionCache.getBuilder().withConnectionFactory(new DefaultPlcDriverManager()).build());
        exampleServer.startup().get();
        int namespaceIndex = exampleServer.getServer().getNamespaceTable().getIndex(Plc4xNamespace.APPLICATIONID)
                .intValue();
        BOOL_IDENTIFIER_READ_WRITE = node(namespaceIndex, "Simulated_BOOL");
        BYTE_IDENTIFIER_READ_WRITE = node(namespaceIndex, "Simulated_BYTE");
        DOUBLE_IDENTIFIER_READ_WRITE = node(namespaceIndex, "Simulated_LREAL");
        FLOAT_IDENTIFIER_READ_WRITE = node(namespaceIndex, "Simulated_REAL");
        INT16_IDENTIFIER_READ_WRITE = node(namespaceIndex, "Simulated_INT");
        INT32_IDENTIFIER_READ_WRITE = node(namespaceIndex, "Simulated_DINT");
        INT64_IDENTIFIER_READ_WRITE = node(namespaceIndex, "Simulated_LINT");
        INTEGER_IDENTIFIER_READ_WRITE = node(namespaceIndex, "Simulated_DINT");
        SBYTE_IDENTIFIER_READ_WRITE = node(namespaceIndex, "Simulated_USINT");
        STRING_IDENTIFIER_READ_WRITE = node(namespaceIndex, "Simulated_STRING");
        UINT16_IDENTIFIER_READ_WRITE = node(namespaceIndex, "Simulated_UINT");
        UINT32_IDENTIFIER_READ_WRITE = node(namespaceIndex, "Simulated_UDINT");
        UINT64_IDENTIFIER_READ_WRITE = node(namespaceIndex, "Simulated_ULINT");
        UINTEGER_IDENTIFIER_READ_WRITE = node(namespaceIndex, "Simulated_UDINT");
    }

    private static String node(int namespaceIndex, String tagName) {
        return "ns=" + namespaceIndex + ";s=yofc/sd/" + tagName;
    }

    private static void seedSimulatedFileValues() throws Exception {
        try (PlcConnection connection = new DefaultPlcDriverManager().getConnection(SIMULATED_CONNECTION_STRING)) {
            PlcWriteRequest.Builder builder = connection.writeRequestBuilder();
            builder.addTagAddress("Bool", "FILE/Simulated:BOOL", true);
            builder.addTagAddress("Byte", "FILE/Simulated:BYTE", 1);
            builder.addTagAddress("Double", "FILE/Simulated:LREAL", 0.5d);
            builder.addTagAddress("Float", "FILE/Simulated:REAL", 0.5f);
            builder.addTagAddress("Int16", "FILE/Simulated:INT", 32000);
            builder.addTagAddress("Int32", "FILE/Simulated:DINT", 42);
            builder.addTagAddress("Int64", "FILE/Simulated:LINT", 42L);
            builder.addTagAddress("SByte", "FILE/Simulated:USINT", 100);
            builder.addTagAddress("String", "FILE/Simulated:STRING", "Hello OPC UA");
            builder.addTagAddress("UInt16", "FILE/Simulated:UINT", 65535);
            builder.addTagAddress("UInt32", "FILE/Simulated:UDINT", 100);
            builder.addTagAddress("UInt64", "FILE/Simulated:ULINT", new BigInteger("1337"));
            builder.build().execute().get();
        }
    }

    @AfterAll
    public static void tearDown() {
        try {
            exampleServer.shutdown().get();
        } catch (Exception e) {

        }
    }

    @Test
    void connectionNoParams() {
        connectionStringValidSet.forEach(connectionAddress -> {
            String connectionString = connectionAddress;
            try {
                PlcConnection opcuaConnection = new DefaultPlcDriverManager().getConnection(connectionString);
                assertTrue(opcuaConnection.isConnected());
                opcuaConnection.close();
                assertFalse(opcuaConnection.isConnected());
            } catch (PlcConnectionException e) {
                fail("Exception during connectionNoParams while connecting Test EXCEPTION: " + e.getMessage());
            } catch (Exception e) {
                fail("Exception during connectionNoParams while closing Test EXCEPTION: " + e.getMessage());
            }

        });

    }

    @Test
    void connectionWithDiscoveryParam() {
        connectionStringValidSet.forEach(connectionAddress -> {
            discoveryParamValidSet.forEach(discoveryParam -> {
                String connectionString = connectionAddress + paramSectionDivider + discoveryParam;
                try {
                    PlcConnection opcuaConnection = new DefaultPlcDriverManager().getConnection(connectionString);
                    assertTrue(opcuaConnection.isConnected());
                    opcuaConnection.close();
                    assertFalse(opcuaConnection.isConnected());
                } catch (PlcConnectionException e) {
                    fail("Exception during connectionWithDiscoveryParam while connecting Test EXCEPTION: "
                            + e.getMessage());
                } catch (Exception e) {
                    fail("Exception during connectionWithDiscoveryParam while closing Test EXCEPTION: "
                            + e.getMessage());
                }
            });
        });

    }

    @Test
    void readVariables() throws Exception {

        PlcConnection opcuaConnection = new DefaultPlcDriverManager().getConnection(tcpConnectionAddress);
        assertTrue(opcuaConnection.isConnected());

        PlcReadRequest.Builder builder = opcuaConnection.readRequestBuilder();
        builder.addTagAddress("Bool", BOOL_IDENTIFIER_READ_WRITE);
        builder.addTagAddress("Byte", BYTE_IDENTIFIER_READ_WRITE);
        builder.addTagAddress("Double", DOUBLE_IDENTIFIER_READ_WRITE);
        builder.addTagAddress("Float", FLOAT_IDENTIFIER_READ_WRITE);
        builder.addTagAddress("Int16", INT16_IDENTIFIER_READ_WRITE);
        builder.addTagAddress("Int32", INT32_IDENTIFIER_READ_WRITE);
        builder.addTagAddress("Int64", INT64_IDENTIFIER_READ_WRITE);
        builder.addTagAddress("Integer", INTEGER_IDENTIFIER_READ_WRITE);
        builder.addTagAddress("SByte", SBYTE_IDENTIFIER_READ_WRITE);
        builder.addTagAddress("String", STRING_IDENTIFIER_READ_WRITE);
        builder.addTagAddress("UInt16", UINT16_IDENTIFIER_READ_WRITE);
        builder.addTagAddress("UInt32", UINT32_IDENTIFIER_READ_WRITE);
        builder.addTagAddress("UInt64", UINT64_IDENTIFIER_READ_WRITE);
        builder.addTagAddress("UInteger", UINTEGER_IDENTIFIER_READ_WRITE);

        builder.addTagAddress("DoesNotExists", DOES_NOT_EXIST_IDENTIFIER_READ_WRITE);

        PlcReadRequest request = builder.build();
        PlcReadResponse response = request.execute().get();

        assertEquals(PlcResponseCode.OK, response.getResponseCode("Bool"));
        assertEquals(PlcResponseCode.OK, response.getResponseCode("Byte"));
        assertEquals(PlcResponseCode.OK, response.getResponseCode("Double"));
        assertEquals(PlcResponseCode.OK, response.getResponseCode("Float"));
        assertEquals(PlcResponseCode.OK, response.getResponseCode("Int16"));
        assertEquals(PlcResponseCode.OK, response.getResponseCode("Int32"));
        assertEquals(PlcResponseCode.OK, response.getResponseCode("Int64"));
        assertEquals(PlcResponseCode.OK, response.getResponseCode("Integer"));
        assertEquals(PlcResponseCode.OK, response.getResponseCode("SByte"));
        assertEquals(PlcResponseCode.OK, response.getResponseCode("String"));
        assertEquals(PlcResponseCode.OK, response.getResponseCode("UInt16"));
        assertEquals(PlcResponseCode.OK, response.getResponseCode("UInt32"));
        assertEquals(PlcResponseCode.OK, response.getResponseCode("UInt64"));
        assertEquals(PlcResponseCode.OK, response.getResponseCode("UInteger"));

        assertEquals(PlcResponseCode.NOT_FOUND, response.getResponseCode("DoesNotExists"));

        opcuaConnection.close();
        assertFalse(opcuaConnection.isConnected());

    }

    @Test
    void writeVariables() throws Exception {

        PlcConnection opcuaConnection = new DefaultPlcDriverManager().getConnection(tcpConnectionAddress);
        assertTrue(opcuaConnection.isConnected());

        PlcWriteRequest.Builder builder = opcuaConnection.writeRequestBuilder();
        builder.addTagAddress("Bool", BOOL_IDENTIFIER_READ_WRITE, true);
        builder.addTagAddress("Byte", BYTE_IDENTIFIER_READ_WRITE + ";BYTE", 255);
        builder.addTagAddress("Double", DOUBLE_IDENTIFIER_READ_WRITE, 0.5d);
        builder.addTagAddress("Float", FLOAT_IDENTIFIER_READ_WRITE, 0.5f);
        builder.addTagAddress("Int16", INT16_IDENTIFIER_READ_WRITE + ";INT", 32000);
        builder.addTagAddress("Int32", INT32_IDENTIFIER_READ_WRITE, 42);
        builder.addTagAddress("Int64", INT64_IDENTIFIER_READ_WRITE, 42L);
        builder.addTagAddress("Integer", INTEGER_IDENTIFIER_READ_WRITE, 42);
        builder.addTagAddress("SByte", SBYTE_IDENTIFIER_READ_WRITE + ";USINT", 100);
        builder.addTagAddress("String", STRING_IDENTIFIER_READ_WRITE, "Helllo Toddy!");
        builder.addTagAddress("UInt16", UINT16_IDENTIFIER_READ_WRITE + ";UINT", 65535);
        builder.addTagAddress("UInt32", UINT32_IDENTIFIER_READ_WRITE + ";UDINT", 100);
        builder.addTagAddress("UInt64", UINT64_IDENTIFIER_READ_WRITE + ";ULINT", new BigInteger("1337"));
        builder.addTagAddress("UInteger", UINTEGER_IDENTIFIER_READ_WRITE + ";UDINT", 100);

        builder.addTagAddress("DoesNotExists", DOES_NOT_EXIST_IDENTIFIER_READ_WRITE, "Sad Toddy");

        PlcWriteRequest request = builder.build();
        PlcWriteResponse response = request.execute().get();
        assertEquals(PlcResponseCode.OK, response.getResponseCode("Bool"));
        assertEquals(PlcResponseCode.OK, response.getResponseCode("Byte"));
        assertEquals(PlcResponseCode.OK, response.getResponseCode("Double"));
        assertEquals(PlcResponseCode.OK, response.getResponseCode("Float"));
        assertEquals(PlcResponseCode.OK, response.getResponseCode("Int16"));
        assertEquals(PlcResponseCode.OK, response.getResponseCode("Int32"));
        assertEquals(PlcResponseCode.OK, response.getResponseCode("Int64"));
        assertEquals(PlcResponseCode.OK, response.getResponseCode("Integer"));
        assertEquals(PlcResponseCode.OK, response.getResponseCode("SByte"));
        assertEquals(PlcResponseCode.OK, response.getResponseCode("String"));
        assertEquals(PlcResponseCode.OK, response.getResponseCode("UInt16"));
        assertEquals(PlcResponseCode.OK, response.getResponseCode("UInt32"));
        assertEquals(PlcResponseCode.OK, response.getResponseCode("UInt64"));
        assertEquals(PlcResponseCode.OK, response.getResponseCode("UInteger"));

        assertEquals(PlcResponseCode.NOT_FOUND, response.getResponseCode("DoesNotExists"));

        opcuaConnection.close();
        assertFalse(opcuaConnection.isConnected());
    }

}
