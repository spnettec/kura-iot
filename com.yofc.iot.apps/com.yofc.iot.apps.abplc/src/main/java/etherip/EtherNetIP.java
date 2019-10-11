/*******************************************************************************
 * Copyright (c) 2012 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package etherip;

import static etherip.protocol.Encapsulation.Command.SEND_RRDATA;
import static etherip.protocol.Encapsulation.Command.UNREGISTER_SESSION;
import static etherip.types.CNPath.MessageRouter;
import static etherip.types.CNService.Get_Attribute_All;
import static etherip.types.CNService.Get_Attribute_Single;

import java.io.ByteArrayOutputStream;
import java.nio.BufferUnderflowException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import etherip.data.CipException;
import etherip.data.ConnectionData;
import etherip.data.EthernetLink;
import etherip.data.Identity;
import etherip.data.InterfaceConfiguration;
import etherip.data.TcpIpInterface;
import etherip.protocol.CIPMultiRequestProtocol;
import etherip.protocol.Connection;
import etherip.protocol.ConnectionDataProtocol;
import etherip.protocol.Encapsulation;
import etherip.protocol.GetConnectionDataProtocol;
import etherip.protocol.GetEthernetLinkProtocol;
import etherip.protocol.GetHexStringDataProtocol;
import etherip.protocol.GetIdentityProtocol;
import etherip.protocol.GetIntAttributeProtocol;
import etherip.protocol.GetInterfaceConfigurationProtocol;
import etherip.protocol.GetPhysicalLinkObjectProtocol;
import etherip.protocol.GetShortAttributeProtocol;
import etherip.protocol.GetStringAttributeProtocol;
import etherip.protocol.GetTcpIpInterfaceProtocol;
import etherip.protocol.ListIdenties;
import etherip.protocol.ListServices;
import etherip.protocol.ListServicesProtocol.Service;
import etherip.protocol.MRChipReadProtocol;
import etherip.protocol.MRChipWriteProtocol;
import etherip.protocol.MessageRouterProtocol;
import etherip.protocol.Protocol;
import etherip.protocol.ProtocolAdapter;
import etherip.protocol.RegisterSession;
import etherip.protocol.SendRRDataProtocol;
import etherip.protocol.TcpConnection;
import etherip.protocol.UdpConnection;
import etherip.protocol.UnconnectedSendProtocol;
import etherip.types.CIPData;
import etherip.types.CNClassPath;
import etherip.types.CNPath;
import etherip.types.CNService;

/**
 * API for communicating via EtherNet/IP
 *
 * @author Kay Kasemir, László Pataki
 */
public class EtherNetIP implements AutoCloseable {

    public static final String VERSION = "1.4";

    public static final Logger logger = LoggerFactory.getLogger(EtherNetIP.class);

    private static final int MAX_REQUEST_SIZE = 400;

    public static final int MAX_WRITE_PAYLOAD = 212;

    private final String address;
    private final int slot;
    private final int port;
    private Connection connection = null;

    public boolean isConnected() {
        return (connection != null && connection.isOpen() && connection.getSession() != 0);
    }

    /**
     * Initialize
     *
     * @param address
     *            IP address of device
     */
    public EtherNetIP(final String address, final int slot) {
        this.address = address;
        this.slot = slot;
        this.port = 44818;
    }

    public EtherNetIP(final String address, final int slot, final int port) {
        this.address = address;
        this.slot = slot;
        this.port = port;
    }

    /**
     * Connect to device via TCP, register session
     */
    public void connectTcp() throws Exception {
        close();
        this.connection = new TcpConnection(this.address, this.slot, this.port);
        this.registerSession();
    }

    public void setTimeoutMs(long timeoutMs) {
        this.connection.setTimeoutMs(timeoutMs);
    }

    /**
     * Connect to device via UDP, register session
     */
    public void connectUdp() throws Exception {
        close();
        this.connection = new UdpConnection(this.address, this.slot, this.port);
        this.registerSession();
    }

    /**
     * List supported services
     * <p>
     * Queries PLC for supported services. Logs them and checks for the required "Communications" service.
     *
     * @return supported services
     * @throws Exception
     *             on error getting services, or when expected service not supported
     */
    public Service[] listServices() throws Exception {
        final ListServices listServices = new ListServices();
        this.connection.execute(listServices);

        final Service[] services = listServices.getServices();
        if (services == null || services.length < 1) {
            throw new Exception("Device does not support EtherIP services");
        }
        logger.info("Service: {}", services[0].getName());
        if (!services[0].getName().toLowerCase().startsWith("comm")) {
            throw new Exception("Expected EtherIP communication service, got " + services[0].getName());
        }
        return services;
    }

    /**
     * List the identities of the rack
     * <p>
     * Queries PLC rack for identities. Logs them and return the identities.
     *
     * @throws Exception
     *             on error getting services
     */
    public Identity[] listIdentity() throws Exception {
        final ListIdenties listIdentities = new ListIdenties();
        this.connection.execute(listIdentities);

        final Identity[] identities = listIdentities.getIdentities();
        for (final Identity identity : identities) {
            logger.info("Identity:{} ", identity);
        }

        return identities;
    }

    /**
     * Register session
     */
    private void registerSession() throws Exception {
        final RegisterSession register = new RegisterSession();
        this.connection.execute(register);
        this.connection.setSession(register.getSession());
    }

    // Turning off the formatter for the gradual form layout in methods.
    // With this style more readable the structure of the encapsulation in the packet.
    // @formatter:off

    public short getShortAttribute(final CNClassPath classPath, final int instance, final int attr) throws Exception {
        final GetShortAttributeProtocol attrProto = new GetShortAttributeProtocol();
        final Encapsulation encap = new Encapsulation(SEND_RRDATA, this.connection.getSession(), new SendRRDataProtocol(
                new MessageRouterProtocol(Get_Attribute_Single, classPath.instance(instance).attr(attr), attrProto)));
        this.connection.execute(encap);
        return attrProto.getValue();
    }

    public short getShortAttribute(final int slot, final CNClassPath classPath, final int instance, final int attr)
            throws Exception {
        final GetShortAttributeProtocol attrProto = new GetShortAttributeProtocol();
        final Encapsulation encap = new Encapsulation(SEND_RRDATA, this.connection.getSession(),
                new SendRRDataProtocol(new UnconnectedSendProtocol(slot, new MessageRouterProtocol(Get_Attribute_Single,
                        classPath.instance(instance).attr(attr), attrProto))));
        this.connection.execute(encap);
        return attrProto.getValue();
    }

    public int getIntAttribute(final CNClassPath classPath, final int instance, final int attr) throws Exception {
        final GetIntAttributeProtocol attrProto = new GetIntAttributeProtocol();
        final Encapsulation encap = new Encapsulation(SEND_RRDATA, this.connection.getSession(), new SendRRDataProtocol(
                new MessageRouterProtocol(Get_Attribute_Single, classPath.instance(instance).attr(attr), attrProto)));
        this.connection.execute(encap);
        return attrProto.getValue();
    }

    public int getIntAttribute(final int slot, final CNClassPath classPath, final int instance, final int attr)
            throws Exception {
        final GetIntAttributeProtocol attrProto = new GetIntAttributeProtocol();
        final Encapsulation encap = new Encapsulation(SEND_RRDATA, this.connection.getSession(),
                new SendRRDataProtocol(new UnconnectedSendProtocol(slot, new MessageRouterProtocol(Get_Attribute_Single,
                        classPath.instance(instance).attr(attr), attrProto))));
        this.connection.execute(encap);
        return attrProto.getValue();
    }

    public String getStringAttribute(final CNClassPath classPath, final int instance, final int attr) throws Exception {
        final GetStringAttributeProtocol attrProto = new GetStringAttributeProtocol();
        final Encapsulation encap = new Encapsulation(SEND_RRDATA, this.connection.getSession(), new SendRRDataProtocol(
                new MessageRouterProtocol(Get_Attribute_Single, classPath.instance(instance).attr(attr), attrProto)));
        this.connection.execute(encap);
        return attrProto.getValue();
    }

    public String getStringAttribute(final int slot, final CNClassPath classPath, final int instance, final int attr)
            throws Exception {
        final GetStringAttributeProtocol attrProto = new GetStringAttributeProtocol();
        final Encapsulation encap = new Encapsulation(SEND_RRDATA, this.connection.getSession(),
                new SendRRDataProtocol(new UnconnectedSendProtocol(slot, new MessageRouterProtocol(Get_Attribute_Single,
                        classPath.instance(instance).attr(attr), attrProto))));
        this.connection.execute(encap);
        return attrProto.getValue();
    }

    public String getHexStringAttribute(final CNClassPath classPath, final int instance, final int attr)
            throws Exception {
        final GetHexStringDataProtocol attrProto = new GetHexStringDataProtocol();
        final Encapsulation encap = new Encapsulation(SEND_RRDATA, this.connection.getSession(), new SendRRDataProtocol(
                new MessageRouterProtocol(Get_Attribute_Single, classPath.instance(instance).attr(attr), attrProto)));
        this.connection.execute(encap);
        return attrProto.getValue();
    }

    public String getHexStringAttribute(final int slot, final CNClassPath classPath, final int instance, final int attr)
            throws Exception {
        final GetHexStringDataProtocol attrProto = new GetHexStringDataProtocol();
        final Encapsulation encap = new Encapsulation(SEND_RRDATA, this.connection.getSession(),
                new SendRRDataProtocol(new UnconnectedSendProtocol(slot, new MessageRouterProtocol(Get_Attribute_Single,
                        classPath.instance(instance).attr(attr), attrProto))));
        this.connection.execute(encap);
        return attrProto.getValue();
    }

    public String getHexStringAttributeAll(final CNClassPath classPath, final int instance) throws Exception {
        final GetHexStringDataProtocol attrProto = new GetHexStringDataProtocol();
        final Encapsulation encap = new Encapsulation(SEND_RRDATA, this.connection.getSession(), new SendRRDataProtocol(
                new MessageRouterProtocol(Get_Attribute_All, classPath.instance(instance), attrProto)));
        this.connection.execute(encap);
        return attrProto.getValue();
    }

    public String getHexStringAttributeAll(final int slot, final CNClassPath classPath, final int instance)
            throws Exception {
        final GetHexStringDataProtocol attrProto = new GetHexStringDataProtocol();
        final Encapsulation encap = new Encapsulation(SEND_RRDATA, this.connection.getSession(),
                new SendRRDataProtocol(new UnconnectedSendProtocol(slot,
                        new MessageRouterProtocol(Get_Attribute_All, classPath.instance(instance), attrProto))));
        this.connection.execute(encap);
        return attrProto.getValue();
    }

    public Identity getIdentity() throws Exception {
        final GetIdentityProtocol attrProto = new GetIdentityProtocol();
        final Encapsulation encap = new Encapsulation(SEND_RRDATA, this.connection.getSession(),
                new SendRRDataProtocol(new MessageRouterProtocol(Get_Attribute_All, CNPath.Identity(), attrProto)));
        this.connection.execute(encap);
        return attrProto.getValue();
    }

    public Identity getSlotIdentity(final int slot) throws Exception {
        final GetIdentityProtocol attrProto = new GetIdentityProtocol();
        final Encapsulation encap = new Encapsulation(SEND_RRDATA, this.connection.getSession(),
                new SendRRDataProtocol(new UnconnectedSendProtocol(slot,
                        new MessageRouterProtocol(Get_Attribute_All, CNPath.Identity(), attrProto))));
        this.connection.execute(encap);
        return attrProto.getValue();
    }

    public ConnectionData getConnectionData() throws Exception {
        final GetConnectionDataProtocol attrProto = new GetConnectionDataProtocol();

        final Encapsulation encap = new Encapsulation(SEND_RRDATA, this.connection.getSession(),
                new SendRRDataProtocol(new ConnectionDataProtocol(attrProto)));
        this.connection.execute(encap);
        return attrProto.getValue();
    }

    public TcpIpInterface getTcpIpInterface(final int instance) throws Exception {
        try {
            final GetTcpIpInterfaceProtocol attrProto = new GetTcpIpInterfaceProtocol();
            final Encapsulation encap = new Encapsulation(SEND_RRDATA, this.connection.getSession(),
                    new SendRRDataProtocol(new MessageRouterProtocol(Get_Attribute_All,
                            CNPath.TcpIpInterface().instance(instance), attrProto)));
            this.connection.execute(encap);

            return attrProto.getValue();
        } catch (final CipException cipException) {
            if (cipException.getStatusCode() == 0x08) {
                /**
                 * In case of Get_Attribute_All service not supported. The service is optional, not always implemented.
                 * 
                 * @see CIP Vol2_1.4: 5-3.3.1
                 */
                return this.getTcpIpInterfaceWithGetAttributeSingle(instance);
            }
            throw cipException;
        } catch (final BufferUnderflowException e) {
            /**
             * In case of the device not correctly implemented CIP possible BufferUnderFlow exception.
             * In this case may the device will answer correctly with Get_Attribute_Single service.
             * 
             * @see CIP Vol2_1.4: 5-3.3.1
             */
            return this.getTcpIpInterfaceWithGetAttributeSingle(instance);
        }
    }

    public TcpIpInterface getTcpIpInterfaceWithGetAttributeSingle(final int instance) throws Exception {
        final TcpIpInterface tcpIpInterface = new TcpIpInterface();
        // setInterfaceConfiguration and setHostName must be the first
        tcpIpInterface.setInterfaceConfiguration(this.getInterfaceConfiguration(instance));
        tcpIpInterface.setHostName(this.getStringAttribute(CNPath.TcpIpInterface(), instance, 6));
        tcpIpInterface.setStatus(this.getIntAttribute(CNPath.TcpIpInterface(), instance, 1));
        tcpIpInterface.setConfigurationCapability(this.getIntAttribute(CNPath.TcpIpInterface(), instance, 2));
        tcpIpInterface.setConfigurationControl(this.getIntAttribute(CNPath.TcpIpInterface(), instance, 3));
        tcpIpInterface.setPhysicalLinkObject(this.getPhysicalLinkObject(instance));

        return tcpIpInterface;
    }

    public CNClassPath getPhysicalLinkObject(final int instance) throws Exception {
        final GetPhysicalLinkObjectProtocol attrProto = new GetPhysicalLinkObjectProtocol();
        final Encapsulation encap = new Encapsulation(SEND_RRDATA, this.connection.getSession(),
                new SendRRDataProtocol(new MessageRouterProtocol(Get_Attribute_Single,
                        CNPath.TcpIpInterface().instance(instance).attr(4), attrProto)));
        this.connection.execute(encap);
        return attrProto;
    }

    public InterfaceConfiguration getInterfaceConfiguration(final int instance) throws Exception {
        final GetInterfaceConfigurationProtocol attrProto = new GetInterfaceConfigurationProtocol();
        final Encapsulation encap = new Encapsulation(SEND_RRDATA, this.connection.getSession(),
                new SendRRDataProtocol(new MessageRouterProtocol(Get_Attribute_Single,
                        CNPath.TcpIpInterface().instance(instance).attr(5), attrProto)));
        this.connection.execute(encap);
        return attrProto.getValue();
    }

    public EthernetLink getEthernetLink(final int instance) throws Exception {
        final GetEthernetLinkProtocol attrProto = new GetEthernetLinkProtocol();
        final Encapsulation encap = new Encapsulation(SEND_RRDATA, this.connection.getSession(), new SendRRDataProtocol(
                new MessageRouterProtocol(Get_Attribute_All, CNPath.EthernetLink().instance(instance), attrProto)));
        this.connection.execute(encap);
        return attrProto.getValue();
    }

    /**
     * Read a single scalar tag
     * 
     * @param tag
     *            Name of tag
     * @return Current value of the tag
     * @throws Exception
     *             on error
     */
    public CIPData readTag(final String tag) throws Exception {
        return this.readTag(tag, (short) 1);
    }

    public void executeRequest(Protocol request) throws Exception {
        final Encapsulation encap = new Encapsulation(SEND_RRDATA, this.connection.getSession(),
                new SendRRDataProtocol(new UnconnectedSendProtocol(slot, request)));
        connection.execute(encap);
    }

    /**
     * Read a single array tag
     * 
     * @param tag
     *            Name of tag
     * @param count
     *            Number of array elements to read
     * @return Current value of the tag
     * @throws Exception
     *             on error
     */
    public CIPData readTag(final String tag, final short count) throws Exception {
        MRChipReadProtocol cipRead = new MRChipReadProtocol(tag, count);
        Encapsulation encap = new Encapsulation(SEND_RRDATA, this.connection.getSession(),
                new SendRRDataProtocol(new UnconnectedSendProtocol(this.slot, cipRead)));
        this.connection.execute(encap);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        outputStream.write(cipRead.getData().getData().array());
        int payLoad = cipRead.getPayLoad();
        while (cipRead.isPartialTransfert()) {

            cipRead = new MRChipReadProtocol(tag, payLoad, count);
            encap = new Encapsulation(SEND_RRDATA, this.connection.getSession(),
                    new SendRRDataProtocol(new UnconnectedSendProtocol(this.slot, cipRead)));
            this.connection.execute(encap);
            outputStream.write(cipRead.getData().getData().array());
            payLoad += cipRead.getPayLoad();
        }
        return new CIPData(cipRead.getData().getType(), outputStream.toByteArray());
    }

    public CIPData readTag(final String tag, final int start, final short count) throws Exception {
        if (start <= 0)
            return readTag(tag, count);
        MRChipReadProtocol cipRead = new MRChipReadProtocol(tag, (short) 1);
        Encapsulation encap = new Encapsulation(SEND_RRDATA, this.connection.getSession(),
                new SendRRDataProtocol(new UnconnectedSendProtocol(this.slot, cipRead)));
        this.connection.execute(encap);
        int vSize = cipRead.getData().getType().getElementSize();
        short arrayCount = (short) (count + start);
        int offsetNums = start * vSize;
        cipRead = new MRChipReadProtocol(tag, offsetNums, arrayCount);
        encap = new Encapsulation(SEND_RRDATA, this.connection.getSession(),
                new SendRRDataProtocol(new UnconnectedSendProtocol(this.slot, cipRead)));
        this.connection.execute(encap);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        outputStream.write(cipRead.getData().getData().array());
        int payLoad = cipRead.getPayLoad() + offsetNums;
        while (cipRead.isPartialTransfert()) {

            cipRead = new MRChipReadProtocol(tag, payLoad, arrayCount);
            encap = new Encapsulation(SEND_RRDATA, this.connection.getSession(),
                    new SendRRDataProtocol(new UnconnectedSendProtocol(this.slot, cipRead)));
            this.connection.execute(encap);
            outputStream.write(cipRead.getData().getData().array());
            payLoad += cipRead.getPayLoad();
        }
        return new CIPData(cipRead.getData().getType(), outputStream.toByteArray());
    }

    /**
     * Read multiple scalar tags in one network transaction
     * 
     * @param tags
     *            Tag names
     * @return Current values of the tags
     * @throws Exception
     *             on error
     */
    public CIPData[] readTags(final String... tags) throws Exception {
        return readTags(100, tags);
    }

    /**
     * Read multiple strings tags in one network transaction.
     * Transaction size is adjsuted for the size of string response.
     * 
     * @param tags
     *            Tag names
     * @return Current values of the tags
     * @throws Exception
     *             on error
     */
    public CIPData[] readStringTags(final String... tags) throws Exception {
        return readTags(5, tags);
    }

    /**
     * Read multiple strings tags in one network transaction.
     * Messages are splitted in groups with a maximum number of elements and total request size.
     * maxGroupSize can be large for small atomic type reads, for large response like string reads, the number should be
     * reduce to make
     * sure the response fit in a controller total response size limit (about 500 bytes for Logix 5000).
     * 
     * @param maxNumberOfRequestsPerGroup
     *            maximum number of requests allowed in a single transaction.
     * @param tags
     *            Tag names
     * @return Current values of the tags
     * @throws Exception
     *             on error
     */
    public CIPData[] readTags(final int maxNumberOfRequestsPerGroup, final String... tags) throws Exception {
        final MRChipReadProtocol[] reads = new MRChipReadProtocol[tags.length];
        for (int i = 0; i < reads.length; ++i) {
            reads[i] = new MRChipReadProtocol(tags[i]);
        }
        sendMultiMessages(maxNumberOfRequestsPerGroup, reads);

        final CIPData[] results = new CIPData[reads.length];
        for (int i = 0; i < results.length; ++i) {
            results[i] = reads[i].getData();
        }

        return results;
    }

    /**
     * send multiple messages in a single transaction.
     * Messages are splitted in groups with a maximum number of elements and total request size.
     * maxGroupSize can be large for small atomic type reads, for large response like string reads, the number should be
     * reduce to make
     * sure the response fit in a controller total response size limit (about 500 bytes for Logix 5000).
     * 
     * @param maxNumberOfRequestsPerGroup
     *            maximum number of requests allowed in a single transaction.
     * @param messages
     *            messages to send
     * @throws Exception
     *             on error
     */
    public void sendMultiMessages(final int maxNumberOfRequestsPerGroup, final MessageRouterProtocol... messages)
            throws Exception {
        int currentGroupByteCount = 0;
        List<MessageRouterProtocol> groupMessages = new ArrayList<>();

        for (MessageRouterProtocol message : messages) {
            int messageSize = message.getRequestSize();
            if (currentGroupByteCount + messageSize > MAX_REQUEST_SIZE
                    || groupMessages.size() == maxNumberOfRequestsPerGroup) {
                executeMultiMessages(groupMessages.toArray(new MessageRouterProtocol[groupMessages.size()]));
                currentGroupByteCount = 0;
                groupMessages.clear();
            }
            currentGroupByteCount += messageSize;
            groupMessages.add(message);
        }
        if (!groupMessages.isEmpty()) {
            executeMultiMessages(groupMessages.toArray(new MessageRouterProtocol[groupMessages.size()]));
        }
    }

    /**
     * send multiple messages to controller in a single transaction.
     * Messages are assumed to fit in controller limits for request and response sizes.
     * 
     * @param messages
     *            messages to send
     * @throws Exception
     *             on error
     */
    private void executeMultiMessages(final MessageRouterProtocol[] messages) throws Exception {
        final Encapsulation encap = new Encapsulation(SEND_RRDATA, this.connection.getSession(), new SendRRDataProtocol(
                new UnconnectedSendProtocol(this.slot, new MessageRouterProtocol(CNService.CIP_MultiRequest,
                        MessageRouter(), new CIPMultiRequestProtocol(messages)))));
        this.connection.execute(encap);
    }

    /**
     * Write a tag
     * 
     * @param tag
     *            Tag name
     * @param value
     *            Value to write
     * @throws Exception
     *             on error
     */
    public void writeTag(final String tag, int start, final CIPData value) throws Exception {
        int vSize = value.getType().getElementSize();
        int arraySize = value.getElementCount();
        if (vSize == 0)
            arraySize = 0;
        int nbrWritCount = 0;
        int totSize = vSize * arraySize;
        int dataOffset = start * vSize;
        if (totSize < MAX_WRITE_PAYLOAD) {
            final MRChipWriteProtocol cipWrite = new MRChipWriteProtocol(tag, (short) arraySize, start, dataOffset,
                    value, CNService.CIP_WriteData);

            final Encapsulation encap = new Encapsulation(SEND_RRDATA, this.connection.getSession(),
                    new SendRRDataProtocol(new UnconnectedSendProtocol(this.slot, cipWrite)));
            this.connection.execute(encap);
        } else {
            int writeCount;
            while (nbrWritCount < arraySize) {
                writeCount = Math.min((MAX_WRITE_PAYLOAD / vSize) - 1, arraySize - nbrWritCount); // Max in 1 packet

                final MRChipWriteProtocol cipWrite = new MRChipWriteProtocol(tag, (short) writeCount, start, dataOffset,
                        value, CNService.CIP_WriteDataFragmented);

                final Encapsulation encap = new Encapsulation(SEND_RRDATA, this.connection.getSession(),
                        new SendRRDataProtocol(new UnconnectedSendProtocol(this.slot, cipWrite)));
                this.connection.execute(encap);
                nbrWritCount += writeCount;
                dataOffset += writeCount * vSize;
            }
        }
    }

    public void writeTag(final String tag, final CIPData value) throws Exception {
        writeTag(tag, 0, value);
    }

    /**
     * Write multiple tags in one network transaction
     * 
     * @param tags
     *            Tag names to write
     * @param values
     *            Values to write
     * @throws Exception
     *             on error
     */
    public void writeTags(final String[] tags, final CIPData[] values) throws Exception {
        if (tags.length != values.length) {
            throw new IllegalArgumentException("Got " + tags.length + " tags but " + values.length + " values");
        }
        final MRChipWriteProtocol[] writes = new MRChipWriteProtocol[tags.length];
        for (int i = 0; i < tags.length; ++i) {
            writes[i] = new MRChipWriteProtocol(tags[i], values[i]);
        }

        final Encapsulation encap = new Encapsulation(SEND_RRDATA, this.connection.getSession(), new SendRRDataProtocol(
                new UnconnectedSendProtocol(this.slot, new MessageRouterProtocol(CNService.CIP_MultiRequest,
                        MessageRouter(), new CIPMultiRequestProtocol(writes)))));
        this.connection.execute(encap);
    }

    // @formatter:on

    /** Unregister session (device will close connection) */
    private void unregisterSession() {
        try {
            if (this.connection.getSession() == 0 || !this.connection.isOpen()) {
                return;
            }
            this.connection
                    .write(new Encapsulation(UNREGISTER_SESSION, this.connection.getSession(), new ProtocolAdapter()));
            // Cannot read after this point because PLC will close the connection
        } catch (final Exception ex) {
            logger.warn("Error un-registering session: {}", ex.getLocalizedMessage(), ex);
        }
    }

    /** Close connection to device */
    @Override
    public void close() throws Exception {
        if (this.connection != null) {
            this.unregisterSession();
            this.connection.close();
            this.connection = null;
        }
    }

    /** @return String representation for debugging */
    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append("EtherIP address '").append(this.address).append("', session 0x")
                .append(Integer.toHexString(this.connection.getSession())).append("\n");
        return buf.toString();
    }
}
