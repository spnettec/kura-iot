package com.yofc.plc4x.driver;

import org.apache.plc4x.java.utils.connectionpool.PooledPlcDriverManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;

@Component(immediate = true)
public class Plc4xActivate {

    private ServiceRegistration<PooledPlcDriverManager> vertxRegistration;

    @Activate
    public void start(BundleContext context) {
        ClassLoader classloader = context.getBundle().adapt(BundleWiring.class).getClassLoader();
        PooledPlcDriverManager pooledPlcDriverManager = new PooledPlcDriverManager(classloader);
        this.vertxRegistration = context.registerService(PooledPlcDriverManager.class, pooledPlcDriverManager, null);
    }

    @Deactivate
    public void stop(BundleContext context) {
        if (vertxRegistration != null) {
            vertxRegistration.unregister();
            vertxRegistration = null;
        }
    }
}
