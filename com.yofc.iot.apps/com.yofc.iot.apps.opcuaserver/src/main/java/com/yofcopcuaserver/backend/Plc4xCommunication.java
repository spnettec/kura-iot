/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.yofcopcuaserver.backend;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ubyte;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ulong;

import java.lang.reflect.Array;
import java.math.BigInteger;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.PlcConnectionManager;
import org.apache.plc4x.java.api.exceptions.PlcConnectionException;
import org.apache.plc4x.java.api.messages.PlcReadRequest;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.apache.plc4x.java.api.messages.PlcWriteRequest;
import org.apache.plc4x.java.api.model.PlcTag;
import org.apache.plc4x.java.api.types.PlcResponseCode;
import org.apache.plc4x.java.api.types.PlcValueType;
import org.apache.plc4x.java.api.value.PlcValue;
import org.apache.plc4x.java.spi.values.PlcBOOL;
import org.apache.plc4x.java.spi.values.PlcLTIME;
import org.apache.plc4x.java.spi.values.PlcTIME;
import org.eclipse.milo.opcua.sdk.server.AbstractLifecycle;
import org.eclipse.milo.opcua.sdk.server.api.DataItem;
import org.eclipse.milo.opcua.sdk.server.nodes.filters.AttributeFilterContext;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.ULong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Plc4xCommunication extends AbstractLifecycle {

    private PlcConnectionManager plcConnectionManager;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Integer DEFAULT_TIMEOUT = 1000000;
    private final Integer DEFAULT_RETRY_BACKOFF = 5000;
    private final DataValue BAD_RESPONSE = new DataValue(new Variant(null), StatusCode.BAD);

    private Map<String, Long> failedConnectionList = new HashMap<>();

    Map<NodeId, DataItem> monitoredList = new HashMap<>();

    public Plc4xCommunication() {

    }

    public Plc4xCommunication(PlcConnectionManager plcConnectionManager) {
        setConnectionManager(plcConnectionManager);
    }

    @Override
    protected void onStartup() {

    }

    @Override
    protected void onShutdown() {
        // Do Nothing
    }

    public PlcConnectionManager getConnectionManager() {
        return plcConnectionManager;
    }

    public void setConnectionManager(PlcConnectionManager plcConnectionManager) {
        this.plcConnectionManager = plcConnectionManager;
    }

    public PlcTag getTag(String tag, String connectionString) throws PlcConnectionException {
        return plcConnectionManager.getDriverManager().getDriverForUrl(connectionString).prepareTag(tag);
    }

    public void addTag(DataItem item) {
        logger.info("Adding item to OPC UA monitored list {}", item.getReadValueId());
        monitoredList.put(item.getReadValueId().getNodeId(), item);
    }

    public void removeTag(DataItem item) {
        logger.info("Removing item from OPC UA monitored list {}", item.getReadValueId());
        monitoredList.remove(item.getReadValueId().getNodeId());
    }

    public static NodeId getNodeId(PlcValueType plcValueType) {
        switch (plcValueType) {
        case BOOL:
            return Identifiers.Boolean;
        case BYTE:
        case USINT:
            return Identifiers.Byte;
        case SINT:
            return Identifiers.SByte;
        case INT:
            return Identifiers.Int16;
        case WORD:
        case UINT:
            return Identifiers.UInt16;
        case DINT:
            return Identifiers.Int32;
        case DWORD:
        case UDINT:
            return Identifiers.UInt32;
        case LINT:
            return Identifiers.Int64;
        case ULINT:
        case LWORD:
            return Identifiers.UInt64;
        case REAL:
            return Identifiers.Float;
        case LREAL:
            return Identifiers.Double;
        case CHAR:
        case WCHAR:
        case STRING:
        case WSTRING:
            return Identifiers.String;
        default:
            return Identifiers.BaseDataType;
        }
    }

    public DataValue getValue(AttributeFilterContext.GetAttributeContext ctx, String tag, String connectionString) {
        PlcConnection connection = null;
        try {

            // Check if we just polled the connection and it failed. Wait for the backoff counter to expire before we
            // try again.
            if (failedConnectionList.containsKey(connectionString)) {
                if (System.currentTimeMillis() > failedConnectionList.get(connectionString) + DEFAULT_RETRY_BACKOFF) {
                    failedConnectionList.remove(connectionString);
                } else {
                    logger.debug("Waiting for back off timer - {}{}",
                            ((failedConnectionList.get(connectionString) + DEFAULT_RETRY_BACKOFF)
                                    - System.currentTimeMillis()),
                            " ms left");
                    return BAD_RESPONSE;
                }
            }

            // Try to connect to PLC
            try {
                connection = plcConnectionManager.getConnection(connectionString);
                logger.debug("{}: Connected", connectionString);
            } catch (PlcConnectionException e) {
                logger.error("Failed to connect to device, error raised - ", e);
                failedConnectionList.put(connectionString, System.currentTimeMillis());
                return BAD_RESPONSE;
            }

            if (!connection.getMetadata().canRead()) {
                logger.error("This connection doesn't support reading.");
                try {
                    connection.close();
                } catch (Exception exception) {
                    logger.warn("Closing connection failed with error - ", exception);
                }
                return BAD_RESPONSE;
            }

            long timeout = DEFAULT_TIMEOUT;
            if (monitoredList.containsKey(ctx.getNode().getNodeId())) {
                timeout = (long) monitoredList.get(ctx.getNode().getNodeId()).getSamplingInterval() * 1000;
            }

            // Create a new read request:
            // - Give the single item requested an alias name
            PlcReadRequest.Builder builder = connection.readRequestBuilder();
            builder.addTagAddress("value-1", tag);
            PlcReadRequest readRequest = builder.build();

            PlcReadResponse response = null;
            try {
                response = readRequest.execute().get(timeout, TimeUnit.MICROSECONDS);
            } catch (InterruptedException e) {
                logger.warn("Failed", e);
                Thread.currentThread().interrupt();
            } catch (ExecutionException | TimeoutException e) {
                logger.warn(" Occurred while reading value, using timeout of {}ms", timeout / 1000, e);
                try {
                    connection.close();
                } catch (Exception exception) {
                    logger.warn("Closing connection failed with error - ", exception);
                }
                return BAD_RESPONSE;
            }
            DataValue resp = BAD_RESPONSE;
            for (String fieldName : response.getTagNames()) {
                if (response.getResponseCode(fieldName) == PlcResponseCode.OK) {
                    int numValues = response.getNumberOfValues(fieldName);
                    if (numValues == 1) {
                        Object obj = response.getObject(fieldName);
                        if (obj instanceof BigInteger) {
                            resp = new DataValue(new Variant(ulong((BigInteger) obj)), StatusCode.GOOD);
                        } else if (obj instanceof Duration) {
                            PlcValue value = response.getPlcValue(fieldName);
                            if (value instanceof PlcTIME) {
                                resp = new DataValue(new Variant(Long.valueOf(((Duration) obj).toMillis())),
                                        StatusCode.GOOD);
                            } else if (value instanceof PlcLTIME) {
                                resp = new DataValue(new Variant(Long.valueOf(((Duration) obj).toNanos())),
                                        StatusCode.GOOD);
                            } else {
                                resp = new DataValue(new Variant(Long.valueOf(((Duration) obj).toDays())),
                                        StatusCode.GOOD);
                            }

                        } else if (obj instanceof LocalDateTime) {
                            LocalDateTime localDateTime = (LocalDateTime) obj;

                            resp = new DataValue(
                                    new Variant(new DateTime(localDateTime.atZone(ZoneId.of("UTC")).toInstant())),
                                    StatusCode.GOOD);
                        } else if (obj instanceof LocalDate) {
                            LocalDate localDate = (LocalDate) obj;
                            resp = new DataValue(
                                    new Variant(new DateTime(localDate.atStartOfDay(ZoneId.of("UTC")).toInstant())),
                                    StatusCode.GOOD);
                        } else {
                            resp = new DataValue(new Variant(obj), StatusCode.GOOD);
                        }
                    } else {
                        Object array = null;
                        if (response.getObject(fieldName, 0) instanceof BigInteger) {
                            array = Array.newInstance(ULong.class, numValues);
                        } else if (response.getObject(fieldName, 0) instanceof Byte) {
                            array = Array.newInstance(Byte.class, numValues);
                        } else if (response.getObject(fieldName, 0) instanceof List
                                && ((List<?>) response.getObject(fieldName, 0)).get(0) instanceof PlcBOOL) {
                            array = Array.newInstance(Byte.class, numValues);
                        } else {
                            array = Array.newInstance(response.getObject(fieldName, 0).getClass(), numValues);
                        }
                        for (int i = 0; i < numValues; i++) {
                            if (response.getObject(fieldName, i) instanceof BigInteger) {
                                Array.set(array, i, ulong((BigInteger) response.getObject(fieldName, i)));
                            } else if (response.getObject(fieldName, i) instanceof Byte) {
                                Array.set(array, i, ubyte((int) response.getObject(fieldName, i)));
                            } else if (response.getObject(fieldName, i) instanceof List
                                    && ((List<?>) response.getObject(fieldName, i)).get(0) instanceof PlcBOOL) {
                                @SuppressWarnings("unchecked")
                                List<PlcValue> list = ((List<PlcValue>) response.getObject(fieldName, i));
                                byte recivedID = 0;
                                for (int j = 0; j < 8; j++) {
                                    recivedID |= list.get(j).getInt() << j;
                                }
                                Array.set(array, i, recivedID);

                            } else {
                                Array.set(array, i, response.getObject(fieldName, i));
                            }
                        }
                        resp = new DataValue(new Variant(array), StatusCode.GOOD);
                    }
                }
            }

            try {
                connection.close();
            } catch (Exception e) {
                failedConnectionList.put(connectionString, System.currentTimeMillis());
                logger.warn("Closing connection failed with error ", e);
            }

            return resp;
        } catch (Exception e) {
            logger.warn("General error reading value {}", e.getStackTrace()[0]);
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception ex) {
                    // Do Nothing
                }
            }
            return BAD_RESPONSE;
        }
    }

    public void setValue(String tag, Object value, String connectionString) {
        PlcConnection connection = null;
        try {
            connection = plcConnectionManager.getConnection(connectionString);
            if (!connection.isConnected()) {
                logger.debug("getConnection() returned a connection that isn't connected");
                connection.connect();
            }
        } catch (PlcConnectionException e) {
            logger.warn("Failed", e);
        }
        if (connection == null) {
            return;
        }

        if (!connection.getMetadata().canWrite()) {
            logger.error("This connection doesn't support writing.");
            try {
                connection.close();
            } catch (Exception e) {
                logger.warn("Closing connection failed with error ", e);
            }
            return;
        }

        // Create a new read request:
        // - Give the single item requested an alias name
        final PlcWriteRequest.Builder builder = connection.writeRequestBuilder();
        if (value instanceof String) {
            String strValue = (String) value;
            // If an array value is passed instead of a single value then convert to a String array
            if ((strValue.charAt(0) == '[') && (strValue.charAt(strValue.length() - 1) == ']')) {
                String[] values = strValue.substring(1, strValue.length() - 1).split(",");
                if (logger.isInfoEnabled()) {
                    logger.info("Adding Tag {}", Arrays.toString(values));
                }
                builder.addTagAddress(tag, tag, (Object[]) values);
            } else {
                builder.addTagAddress(tag, tag, strValue);
            }
        } else {
            builder.addTagAddress(tag, tag, value);
        }

        PlcWriteRequest writeRequest = builder.build();

        try {
            writeRequest.execute().get();
        } catch (InterruptedException e) {
            logger.warn("Failed", e);
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            logger.warn("Failed", e);
        }

        try {
            connection.close();
        } catch (Exception e) {
            logger.warn("Closing Connection Failed with error ", e);
        }
    }
}
