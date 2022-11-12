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

import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.math.BigInteger;

import org.apache.commons.io.FileUtils;
import org.apache.plc4x.java.PlcDriverManager;
import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.exceptions.PlcConnectionException;
import org.apache.plc4x.java.api.messages.PlcReadRequest;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.apache.plc4x.java.api.messages.PlcWriteRequest;
import org.apache.plc4x.java.api.messages.PlcWriteResponse;
import org.apache.plc4x.java.api.types.PlcResponseCode;
import org.apache.plc4x.java.utils.connectionpool.PooledPlcDriverManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.yofc.opcuaserver.OPCUAServer;

import io.vavr.collection.List;

/**
 */
class OpcuaPlcDriverTest {

    // Read only variables of milo example server of version 3.6
    private static final String BOOL_IDENTIFIER_READ_WRITE = "ns=1;s=Simulated_BOOL";
    private static final String BYTE_IDENTIFIER_READ_WRITE = "ns=1;s=Simulated_BYTE";
    private static final String DOUBLE_IDENTIFIER_READ_WRITE = "ns=1;s=Simulated_LREAL";
    private static final String FLOAT_IDENTIFIER_READ_WRITE = "ns=1;s=Simulated_REAL";
    private static final String INT16_IDENTIFIER_READ_WRITE = "ns=1;s=Simulated_INT";
    private static final String INT32_IDENTIFIER_READ_WRITE = "ns=1;s=Simulated_DINT";
    private static final String INT64_IDENTIFIER_READ_WRITE = "ns=1;s=Simulated_LINT";
    private static final String INTEGER_IDENTIFIER_READ_WRITE = "ns=1;s=Simulated_DINT";
    private static final String SBYTE_IDENTIFIER_READ_WRITE = "ns=1;s=Simulated_USINT";
    private static final String STRING_IDENTIFIER_READ_WRITE = "ns=1;s=Simulated_STRING";
    private static final String UINT16_IDENTIFIER_READ_WRITE = "ns=1;s=Simulated_UINT";
    private static final String UINT32_IDENTIFIER_READ_WRITE = "ns=1;s=Simulated_UDINT";
    private static final String UINT64_IDENTIFIER_READ_WRITE = "ns=1;s=Simulated_ULINT";
    private static final String UINTEGER_IDENTIFIER_READ_WRITE = "ns=1;s=Simulated_UDINT";
    private static final String DOES_NOT_EXIST_IDENTIFIER_READ_WRITE = "ns=1;i=12512623";
    // Address of local milo server
    private String miloLocalAddress = "127.0.0.1:12673/iot";
    // Tcp pattern of OPC UA
    private String opcPattern = "opcua:tcp://";

    private String paramSectionDivider = "?";
    private String tcpConnectionAddress = opcPattern + miloLocalAddress;

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
        exampleServer = new OPCUAServer("src/test/resources/config.yml", true, new PooledPlcDriverManager());
        exampleServer.startup().get();
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
                PlcConnection opcuaConnection = new PlcDriverManager().getConnection(connectionString);
                assert opcuaConnection.isConnected();
                opcuaConnection.close();
                assert !opcuaConnection.isConnected();
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
                    PlcConnection opcuaConnection = new PlcDriverManager().getConnection(connectionString);
                    assert opcuaConnection.isConnected();
                    opcuaConnection.close();
                    assert !opcuaConnection.isConnected();
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

        PlcConnection opcuaConnection = new PlcDriverManager().getConnection(tcpConnectionAddress);
        assert opcuaConnection.isConnected();

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

        assert response.getResponseCode("Bool").equals(PlcResponseCode.OK);
        assert response.getResponseCode("Byte").equals(PlcResponseCode.OK);
        assert response.getResponseCode("Double").equals(PlcResponseCode.OK);
        assert response.getResponseCode("Float").equals(PlcResponseCode.OK);
        assert response.getResponseCode("Int16").equals(PlcResponseCode.OK);
        assert response.getResponseCode("Int32").equals(PlcResponseCode.OK);
        assert response.getResponseCode("Int64").equals(PlcResponseCode.OK);
        assert response.getResponseCode("Integer").equals(PlcResponseCode.OK);
        assert response.getResponseCode("SByte").equals(PlcResponseCode.OK);
        assert response.getResponseCode("String").equals(PlcResponseCode.OK);
        assert response.getResponseCode("UInt16").equals(PlcResponseCode.OK);
        assert response.getResponseCode("UInt32").equals(PlcResponseCode.OK);
        assert response.getResponseCode("UInt64").equals(PlcResponseCode.OK);
        assert response.getResponseCode("UInteger").equals(PlcResponseCode.OK);

        assert response.getResponseCode("DoesNotExists").equals(PlcResponseCode.NOT_FOUND);

        opcuaConnection.close();
        assert !opcuaConnection.isConnected();

    }

    @Test
    void writeVariables() throws Exception {

        PlcConnection opcuaConnection = new PlcDriverManager().getConnection(tcpConnectionAddress);
        assert opcuaConnection.isConnected();

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
        assert response.getResponseCode("Bool").equals(PlcResponseCode.OK);
        assert response.getResponseCode("Byte").equals(PlcResponseCode.OK);
        assert response.getResponseCode("Double").equals(PlcResponseCode.OK);
        assert response.getResponseCode("Float").equals(PlcResponseCode.OK);
        assert response.getResponseCode("Int16").equals(PlcResponseCode.OK);
        assert response.getResponseCode("Int32").equals(PlcResponseCode.OK);
        assert response.getResponseCode("Int64").equals(PlcResponseCode.OK);
        assert response.getResponseCode("Integer").equals(PlcResponseCode.OK);
        assert response.getResponseCode("SByte").equals(PlcResponseCode.OK);
        assert response.getResponseCode("String").equals(PlcResponseCode.OK);
        assert response.getResponseCode("UInt16").equals(PlcResponseCode.OK);
        assert response.getResponseCode("UInt32").equals(PlcResponseCode.OK);
        assert response.getResponseCode("UInt64").equals(PlcResponseCode.OK);
        assert response.getResponseCode("UInteger").equals(PlcResponseCode.OK);

        assert response.getResponseCode("DoesNotExists").equals(PlcResponseCode.NOT_FOUND);

        opcuaConnection.close();
        assert !opcuaConnection.isConnected();
    }

}
