package com.yofc.iot.jolokia;

import java.lang.management.ManagementFactory;

import javax.management.MBeanServer;

import org.apache.aries.jmx.Logger;
import org.apache.aries.jmx.agent.JMXAgent;
import org.apache.aries.jmx.agent.JMXAgentImpl;
import org.apache.aries.jmx.framework.StateConfig;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.log.LogService;

public class Activator implements BundleActivator {

    private ServiceRegistration<?> handle;
    private JMXAgent agent;
    private Logger logger;

    @Override
    public void start(final BundleContext context) throws Exception {
        final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        if (server != null) {
            this.handle = context.registerService(MBeanServer.class.getName(), server, null);
        }
        StateConfig stateConfig = StateConfig.register(context);
        logger = new Logger(context);
        // starting logger
        logger.open();
        logger.log(LogService.LOG_DEBUG, "Starting JMX OSGi bundle");
        agent = new JMXAgentImpl(context, stateConfig, logger);
        agent.start();
    }

    @Override
    public void stop(final BundleContext context) throws Exception {
        if (this.handle != null) {
            this.handle.unregister();
            this.handle = null;
        }
        if (logger != null) {
            logger.log(LogService.LOG_DEBUG, "Stopping JMX OSGi bundle");
        }
        if (agent != null) {
            agent.stop();
        }
        if (logger != null) {
            logger.close();
        }
    }

}