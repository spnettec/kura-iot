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

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

import java.lang.reflect.Array;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

import org.apache.plc4x.java.api.exceptions.PlcConnectionException;
import org.apache.plc4x.java.utils.connectionpool.PooledPlcDriverManager;
import org.eclipse.milo.opcua.sdk.core.AccessLevel;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.core.ValueRank;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.DataItem;
import org.eclipse.milo.opcua.sdk.server.api.ManagedNamespaceWithLifecycle;
import org.eclipse.milo.opcua.sdk.server.api.MonitoredItem;
import org.eclipse.milo.opcua.sdk.server.dtd.DataTypeDictionaryManager;
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.sdk.server.nodes.filters.AttributeFilters;
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yofc.opcuaserver.configuration.Configuration;
import com.yofc.opcuaserver.configuration.DeviceConfiguration;
import com.yofc.opcuaserver.configuration.Tag;

public class Plc4xNamespace extends ManagedNamespaceWithLifecycle {

    public static final String APPLICATIONID = "urn:com:yofc:iot:server";

    private Configuration config;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final DataTypeDictionaryManager dictionaryManager;
    private final SubscriptionModel subscriptionModel;
    private Plc4xCommunication plc4xServer;

    public Plc4xNamespace(OpcUaServer server, Configuration c, PooledPlcDriverManager pooledPlcDriverManager) {
        super(server, APPLICATIONID);
        this.config = c;
        subscriptionModel = new SubscriptionModel(server, this);
        dictionaryManager = new DataTypeDictionaryManager(getNodeContext(), APPLICATIONID);
        plc4xServer = new Plc4xCommunication(pooledPlcDriverManager);

        getLifecycleManager().addLifecycle(dictionaryManager);
        getLifecycleManager().addLifecycle(subscriptionModel);
        getLifecycleManager().addLifecycle(plc4xServer);
        getLifecycleManager().addStartupTask(this::addNodes);
    }

    private void addNodes() {
        String rootName = config.getName();
        String rootAlias = config.getAlias();
        if (rootAlias == null || rootAlias.equals("")) {
            rootAlias = rootName;
        }
        if (rootName == null || rootName.equals("")) {
            rootName = rootAlias;
        }
        NodeId rootNodeId = newNodeId(rootName);

        UaFolderNode rootFolderNode = new UaFolderNode(getNodeContext(), rootNodeId, newQualifiedName(rootName),
                LocalizedText.english(rootAlias));
        getNodeManager().addNode(rootFolderNode);

        rootFolderNode.addReference(new Reference(rootFolderNode.getNodeId(), Identifiers.Organizes,
                Identifiers.ObjectsFolder.expanded(), false));
        for (DeviceConfiguration c : config.getDevices()) {
            String folderName = c.getName();
            String folderAlias = c.getAlias();
            if (folderAlias == null || folderAlias.equals("")) {
                folderAlias = folderName;
            }
            if (folderName == null || folderName.equals("")) {
                folderName = folderAlias;
            }
            NodeId folderNodeId = newNodeId(rootName + "/" + folderName);
            if (getNodeManager().containsNode(folderNodeId)) {
                logger.error("folderNodeId:{} already exist", folderNodeId);
                return;
            }
            UaFolderNode folderNode = new UaFolderNode(getNodeContext(), folderNodeId, newQualifiedName(folderAlias),
                    LocalizedText.english(folderAlias));

            getNodeManager().addNode(folderNode);

            rootFolderNode.addOrganizes(folderNode);

            addConfiguredNodes(folderNode, c);
        }
    }

    private void addConfiguredNodes(UaFolderNode rootNode, DeviceConfiguration c) {
        final List<Tag> tags = c.getTags();
        final String connectionString = c.getConnectionString();
        for (Tag tag : tags) {
            String tagName = tag.getName();
            String tagAlias = tag.getAlias();
            if (tagAlias == null || tagAlias.equals("")) {
                tagAlias = tagName;
            }
            if (tagName == null || tagName.equals("")) {
                tagName = tagAlias;
            }
            String nodeName = rootNode.getNodeId().getIdentifier() + "/" + tagName;
            String nodeAlias = tagAlias;
            NodeId nodeId = newNodeId(nodeName);
            if (getNodeManager().containsNode(nodeId)) {
                logger.error("nodeId:{} already exist", nodeId);
                return;
            }
            logger.info("Adding Tag {}-{}", nodeName, tag.getAddress());
            final String addr = tag.getAddress();

            Class<?> datatype = null;
            NodeId typeId = Identifiers.String;
            UaVariableNode node = null;
            Variant variant = null;
            try {
                datatype = plc4xServer.getField(addr, connectionString).getDefaultJavaType();
                final int length = plc4xServer.getField(addr, connectionString).getNumberOfElements();
                typeId = Plc4xCommunication.getNodeId(plc4xServer.getField(addr, connectionString).getPlcDataType());

                if (length > 1) {
                    node = new UaVariableNode.UaVariableNodeBuilder(getNodeContext()).setNodeId(nodeId)
                            .setAccessLevel(AccessLevel.READ_WRITE).setUserAccessLevel(AccessLevel.READ_WRITE)
                            .setBrowseName(newQualifiedName(nodeName)).setDisplayName(LocalizedText.english(nodeAlias))
                            .setDataType(typeId).setTypeDefinition(Identifiers.BaseDataVariableType)
                            .setValueRank(ValueRank.OneDimension.getValue())
                            .setArrayDimensions(new UInteger[] { uint(length) }).build();

                    Object array = Array.newInstance(datatype, length);
                    for (int j = 0; j < length; j++) {
                        if (datatype.isPrimitive()) {
                            if (datatype.getName().equals("char")) {
                                Array.set(array, j, Character.valueOf('\0'));
                            } else if (datatype.getName().equals("int") || datatype.getName().equals("float")
                                    || datatype.getName().equals("double") || datatype.getName().equals("long")) {
                                Array.set(array, j, 0);
                            } else {
                                Array.set(array, j, false);
                            }
                        } else {
                            Array.set(array, j, "");
                        }
                    }
                    variant = new Variant(array);
                } else {
                    node = new UaVariableNode.UaVariableNodeBuilder(getNodeContext()).setNodeId(nodeId)
                            .setAccessLevel(AccessLevel.READ_WRITE).setUserAccessLevel(AccessLevel.READ_WRITE)
                            .setBrowseName(newQualifiedName(nodeName)).setDisplayName(LocalizedText.english(nodeAlias))
                            .setDataType(typeId).setTypeDefinition(Identifiers.BaseDataVariableType).build();
                    variant = new Variant(0);
                }

                node.setValue(new DataValue(variant));

                node.getFilterChain()
                        .addLast(AttributeFilters.getValue(ctx -> plc4xServer.getValue(ctx, addr, connectionString)));

                node.getFilterChain().addLast(AttributeFilters.setValue((ctx, value) -> {
                    if (length > 1) {
                        plc4xServer.setValue(addr, Arrays.toString((Object[]) value.getValue().getValue()),
                                connectionString);
                    } else {
                        Object v;
                        if (value.getValue().getValue() instanceof DateTime) {
                            DateTime dateTime = (DateTime) value.getValue().getValue();
                            v = LocalDateTime.ofInstant(dateTime.getJavaInstant(), ZoneId.of("UTC"));
                        } else {
                            v = value.getValue().getValue();
                        }
                        plc4xServer.setValue(addr, v, connectionString);
                    }

                }));

            } catch (PlcConnectionException e) {
                logger.error("Couldn't find data type", e);

            }

            getNodeManager().addNode(node);
            rootNode.addOrganizes(node);
        }
    }

    @Override
    public void onDataItemsCreated(List<DataItem> dataItems) {
        for (DataItem item : dataItems) {
            plc4xServer.addField(item);

            if (plc4xServer.getDriverManager() == null) {
                plc4xServer.removeField(item);
                plc4xServer.setDriverManager(new PooledPlcDriverManager());
            }
        }

        subscriptionModel.onDataItemsCreated(dataItems);
    }

    @Override
    public void onDataItemsModified(List<DataItem> dataItems) {
        for (DataItem item : dataItems) {
            plc4xServer.addField(item);
        }
        subscriptionModel.onDataItemsModified(dataItems);
    }

    @Override
    public void onDataItemsDeleted(List<DataItem> dataItems) {
        for (DataItem item : dataItems) {
            plc4xServer.removeField(item);
        }
        subscriptionModel.onDataItemsDeleted(dataItems);
    }

    @Override
    public void onMonitoringModeChanged(List<MonitoredItem> monitoredItems) {
        logger.info(" 4 - {}", monitoredItems);
        for (MonitoredItem item : monitoredItems) {
            logger.info(" 4 - {}", item);
        }
        subscriptionModel.onMonitoringModeChanged(monitoredItems);
    }

}
