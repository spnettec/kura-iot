package com.yofc.plc4x.driver;

import java.util.Hashtable;

import org.apache.plc4x.java.DefaultPlcDriverManager;
import org.apache.plc4x.java.api.PlcConnectionManager;
import org.apache.plc4x.java.utils.cache.CachedPlcConnectionManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(immediate = true)
public class Plc4xActivate {

    private static final Logger logger = LoggerFactory.getLogger(Plc4xActivate.class);

    private ServiceRegistration<PlcConnectionManager> vertxRegistration;
    private CachedPlcConnectionManager plcConnectionManager;

    @Activate
    public void start(BundleContext context) {
        ClassLoader classloader = context.getBundle().adapt(BundleWiring.class).getClassLoader();
        plcConnectionManager = CachedPlcConnectionManager.getBuilder()
                .withConnectionManager(new DefaultPlcDriverManager(classloader)).build();
        final Hashtable<String, String> props = new Hashtable<>();
        props.put("kura.service.pid", "com.yofc.plc4x.driver.plcConnectionManager");
        this.vertxRegistration = context.registerService(PlcConnectionManager.class, plcConnectionManager, props);
    }

    @Deactivate
    public void stop(BundleContext context) {
        if (vertxRegistration != null) {
            vertxRegistration.unregister();
            vertxRegistration = null;
        }
        // Close the cache so every cached PlcConnection releases its underlying fd / socket.
        // Without this an OSGi bundle redeploy leaves orphaned SerialPort instances pinning
        // serial fds (TIOCEXCL exclusive lock); the next bundle generation then fails to reopen
        // /dev/ttyUSB* until the JVM itself restarts.
        if (plcConnectionManager != null) {
            try {
                plcConnectionManager.close();
            } catch (Exception e) {
                logger.warn("Failed to close CachedPlcConnectionManager", e);
            }
            plcConnectionManager = null;
        }
    }
}
