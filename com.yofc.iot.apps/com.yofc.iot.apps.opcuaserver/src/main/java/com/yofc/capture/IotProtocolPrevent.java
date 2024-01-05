package com.yofc.capture;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Random;

import org.eclipse.kura.KuraException;
import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.executor.Command;
import org.eclipse.kura.executor.CommandExecutorService;
import org.eclipse.kura.executor.CommandStatus;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.pcap4j.core.PcapNativeException;
import org.pcap4j.core.PcapNetworkInterface;
import org.pcap4j.core.Pcaps;
import org.pcap4j.packet.IpV4Packet;
import org.pcap4j.packet.TcpPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yofc.capture.IotProtocolPrevent.Config;
import com.yofc.capture.PacketCaptureListener.Callback;
import com.yofc.iot.component.api.MqttClientService;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import io.vertx.core.json.JsonObject;

@Designate(ocd = Config.class, factory = true)
@Component(configurationPolicy = ConfigurationPolicy.REQUIRE, immediate = true)
public class IotProtocolPrevent implements ConfigurableComponent {

    private static final Logger logger = LoggerFactory.getLogger(IotProtocolPrevent.class);
    @Reference(name = "CommandExecutorService", service = org.eclipse.kura.executor.PrivilegedExecutorService.class, policy = ReferencePolicy.STATIC, cardinality = ReferenceCardinality.OPTIONAL)
    private CommandExecutorService executorService;

    @Reference(name = "MqttClientService", policy = ReferencePolicy.DYNAMIC, cardinality = ReferenceCardinality.OPTIONAL)
    private volatile MqttClientService mqttClientService;

    private PacketCaptureThread pThead;
    private Config config = null;
    private Random random = new Random();

    private Script script = null;

    @Activate
    public void start(final BundleContext context, Config properties) throws KuraException {
        logger.info("start active IotProtocolPrevent");
        updateConfig(properties);
        logger.info("done active IotProtocolPrevent");
    }

    @Modified
    public void update(Config properties) throws KuraException {
        logger.info("start update IotProtocolPrevent");
        updateConfig(properties);
        logger.info("done update IotProtocolPrevent");
    }

    @Deactivate
    public void deactive() {
        logger.info("start deactive IotProtocolPrevent");
        stop();
        logger.info("done deactive IotProtocolPrevent");
    }

    public void stop() {
        if (pThead != null) {
            logger.info("stopping loop thead");
            pThead.breakLoop();
            try {
                pThead.join();
            } catch (InterruptedException e) {
                pThead.interrupt();
            } finally {
                pThead = null;
            }
        }
        this.config = null;
    }

    private void updateConfig(Config properties) throws KuraException {
        String scriptSource = "";
        stop();
        if (!properties.activate()) {
            logger.info("disenable IotProtocolPrevent");
            return;
        }
        logger.info("enable IotProtocolPrevent");
        this.config = properties;
        scriptSource = properties.script();
        Binding binding = new Binding();
        GroovyShell shell = new GroovyShell(binding);
        binding.setVariable("logger", logger);
        if (!"".equals(scriptSource)) {
            try {
                script = shell.parse(scriptSource);
            } catch (Exception e) {
                logger.error("Script parse error", e);
                script = null;
            }
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
        listener.setCallback(callback);
        if (properties.filterExpression() != null && !properties.filterExpression().equals("")) {
            builder.withFilterExpression(properties.filterExpression());
        }
        pThead = builder.withDevice(device).withTimeout(properties.timeout()).withListener(listener).build();
        pThead.setName("IotProtocolPrevent-" + random.nextInt());
        pThead.start();
    }

    private Callback callback = (packet, rawPacket) -> {
        if (packet == null || packet.getRawData() == null || packet.getRawData().length == 0) {
            return;
        }
        if (!(rawPacket.contains(IpV4Packet.class) && rawPacket.contains(TcpPacket.class))) {
            return;
        }
        if (IotProtocolPrevent.this.script != null) {
            IotProtocolPrevent.this.script.setProperty("packet", packet.getRawData());
            IotProtocolPrevent.this.script.setProperty("rawPacket", rawPacket.getRawData());
            IotProtocolPrevent.this.script.setProperty("result", false);
            IotProtocolPrevent.this.script.run();
            Object output = IotProtocolPrevent.this.script.getProperty("result");
            if (output instanceof Boolean && output == Boolean.TRUE) {

                IpV4Packet ipv4 = rawPacket.get(IpV4Packet.class);
                TcpPacket tcp = rawPacket.get(TcpPacket.class);
                String srcIp = ipv4.getHeader().getSrcAddr().getHostName();
                String distPort = tcp.getHeader().getDstPort().valueAsString();
                boolean blocked = false;
                if (!IotProtocolPrevent.this.isExit(srcIp, distPort, "forward-kura-ipf")) {
                    IotProtocolPrevent.this.preventTarget(srcIp, distPort, "forward-kura-ipf");
                    blocked = true;
                }
                if (!IotProtocolPrevent.this.isExit(srcIp, distPort, "INPUT")) {
                    IotProtocolPrevent.this.preventTarget(srcIp, distPort, "INPUT");
                    blocked = true;
                }
                if (blocked) {
                    logger.warn("Detected Risk!!! ip {} port {}", srcIp, distPort);
                }
                if (IotProtocolPrevent.this.mqttClientService != null) {
                    try {
                        JsonObject obj = new JsonObject();
                        obj.put("srcHost", ipv4.getHeader().getSrcAddr().getHostName());
                        obj.put("srcPort", tcp.getHeader().getSrcPort().valueAsString());
                        obj.put("dstHost", ipv4.getHeader().getDstAddr().getHostName());
                        obj.put("dstPort", tcp.getHeader().getDstPort().valueAsString());
                        obj.put("data", packet.getRawData());
                        IotProtocolPrevent.this.mqttClientService.publish(IotProtocolPrevent.this.config.mqttTopic(), 0,
                                false, obj.toString());
                    } catch (KuraException e) {
                        logger.error("error send mqtt message", e);
                    }
                }
            }
        }
    };

    private void preventTarget(String ip, String port, String table) {
        CommandStatus status;
        if (this.executorService != null && Objects.nonNull(ip)) {
            String command = "iptables -%s %s -s %s %s -j DROP";
            String portStr = "";
            if (port == null) {
                portStr = "";
            } else {
                portStr = "-ptcp --dport " + port;
            }
            String forwardCommand = command.formatted("I", table, ip, portStr);
            status = execute(forwardCommand);
            if (!status.getExitStatus().isSuccessful()) {
                logger.error("Failed to prevent ip {} and port {} {}", ip, port, table);
            }
        }
    }

    private boolean isExit(String ip, String port, String table) {
        String command = "iptables -%s %s -s %s %s -j DROP";
        String portStr = "";
        if (port == null) {
            portStr = "";
        } else {
            portStr = "-ptcp --dport " + port;
        }
        CommandStatus status = execute(command.formatted("C", table, ip, portStr));
        return status.getExitStatus().isSuccessful();
    }

    private CommandStatus execute(String commandLine) {
        Command command = new Command(commandLine.split(" "));
        command.setExecuteInAShell(true);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        command.setErrorStream(err);
        command.setOutputStream(out);
        CommandStatus status = this.executorService.execute(command);
        if (logger.isDebugEnabled()) {
            logger.debug("execute command {} :: exited with code - {}", command, status.getExitStatus().getExitCode());
            logger.debug("execute stderr {}", new String(err.toByteArray(), StandardCharsets.UTF_8));
            logger.debug("execute stdout {}", new String(out.toByteArray(), StandardCharsets.UTF_8));
        }
        return status;
    }

    @ObjectClassDefinition(id = "com.yofc.capture.IotProtocolPrevent", name = "%name", description = "%description", localization = "OSGI-INF/l10n/com.yofc.capture.IotProtocolPreventCfg")
    @interface Config {

        @AttributeDefinition(name = "%activate", description = "%activateDesc")
        boolean activate() default false;

        @AttributeDefinition(name = "%intefaceName", description = "%intefaceNameDesc")
        String intefaceName() default "eth0";

        @AttributeDefinition(name = "%filterExpression", description = "%filterExpressionDesc", required = false)
        String filterExpression() default "dst port 102";

        @AttributeDefinition(name = "%timeout", description = "%timeoutDesc")
        int timeout() default 20;

        @AttributeDefinition(name = "%serviceTarget", cardinality = 0, description = "%serviceTargetDesc", required = false)
        String MqttClientService_target() default "(kura.service.pid=changeme)";

        @AttributeDefinition(name = "%mqttTopic", cardinality = 0, description = "%mqttTopicDesc", required = false)
        String mqttTopic() default "yofc/protect";

        @AttributeDefinition(name = "%script", cardinality = 0, description = "%scriptDesc", required = false)
        String script() default "";

    }
}
