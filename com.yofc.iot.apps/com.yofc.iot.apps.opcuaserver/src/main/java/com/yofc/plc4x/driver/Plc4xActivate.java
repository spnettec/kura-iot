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

@Component(immediate = true)
public class Plc4xActivate {

    private ServiceRegistration<PlcConnectionManager> vertxRegistration;

    @Activate
    public void start(BundleContext context) {
        ClassLoader classloader = context.getBundle().adapt(BundleWiring.class).getClassLoader();
        PlcConnectionManager plcConnectionManager = CachedPlcConnectionManager
                .getBuilder(new DefaultPlcDriverManager(classloader)).build();
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
    }
}
