package com.yofc.capture;

import org.eclipse.kura.KuraException;
import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.executor.CommandExecutorService;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.pcap4j.core.PcapNativeException;
import org.pcap4j.core.PcapNetworkInterface;
import org.pcap4j.core.Pcaps;
import org.pcap4j.packet.IpV4Packet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Designate(ocd = com.yofc.capture.IotProtocolPrevent.Config.class, factory = true)
@Component(configurationPolicy = ConfigurationPolicy.REQUIRE, immediate = true)
public class IotProtocolPrevent implements ConfigurableComponent {

    private final Logger logger = LoggerFactory.getLogger(IotProtocolPrevent.class);
    @Reference
    private CommandExecutorService executorService;

    private PacketCaptureThread pThead;
    private Config preConfig = null;

    @Activate
    public void start(final BundleContext context, Config properties) throws KuraException {
        this.preConfig = properties;
        updateConfig(properties);
    }

    @Modified
    public void update(Config properties) throws KuraException {
        if (this.preConfig != null && properties.toString().equals(this.preConfig.toString())) {
            return;
        }
        this.preConfig = properties;
        updateConfig(properties);
    }

    @Deactivate
    public void stop() {
        if (pThead != null) {
            pThead.breakLoop();
            pThead = null;
        }
    }

    private void updateConfig(Config properties) throws KuraException {
        if (!properties.activate()) {
            stop();
            return;
        }
        PacketCaptureThread.Builder builder = new PacketCaptureThread.Builder();
        PcapNetworkInterface device = null;
        try {
            device = Pcaps.getDevByName(properties.intefaceName());
        } catch (PcapNativeException e) {
            throw KuraException.internalError(e, "get interface error");
        }
        if (device == null) {
            logger.debug("");
            throw new IllegalArgumentException("Could not open output device eth0");
        }
        PacketCaptureListener listener = new PacketCaptureListener();
        listener.setCallback((packet, rawPacket) -> {

            if (packet == null) {
                return;
            }
            if (isS7Packet(packet.getRawData())) {
                IpV4Packet p = rawPacket.get(IpV4Packet.class);
                if (p != null) {
                    logger.info("src ip:{}", p.getHeader().getSrcAddr());
                    logger.info("desc ip:{}", p.getHeader().getDstAddr());
                }
                logger.info("S7 packet:{}", packet);
            }
        });
        pThead = builder.withDevice(device).withTimeout(properties.timeout())
                .withFilterExpression(properties.filterExpression()).withListener(listener).build();
        pThead.setDaemon(true);
        pThead.start();
    }

    private static boolean isS7Packet(byte[] bytes) {

        if (bytes.length < 4 && bytes[0] != 0x03) {
            return false;
        }
        int len = (4 << bytes[2]) | (bytes[3] & 0xff);
        return bytes.length == len;
    }

    @ObjectClassDefinition(id = "com.yofc.capture.IotProtocolPrevent", name = "%name", description = "%description", localization = "OSGI-INF/l10n/com.yofc.capture.IotProtocolPreventCfg")
    @interface Config {

        @AttributeDefinition(name = "%activate", description = "%activateDesc")
        boolean activate() default false;

        @AttributeDefinition(name = "%intefaceName", description = "%intefaceNameDesc")
        String intefaceName() default "";

        @AttributeDefinition(name = "%filterExpression", description = "%filterExpressionDesc")
        String filterExpression() default "dst port 102";

        @AttributeDefinition(name = "%timeout", description = "%timeoutDesc")
        int timeout() default 20;
    }
}
