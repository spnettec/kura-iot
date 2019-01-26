package com.yofc.iot.component.impl;

import java.util.Dictionary;
import java.util.Hashtable;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

import com.yofc.vertx.osgi.api.Utils;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;

@Component(immediate = true)
public class H2JDBCClientProvider {

    @Reference
    private Vertx vertx;

    private ServiceRegistration<JDBCClient> registration;
    private JDBCClient client;

    @Activate
    public void registerH2JDBCClientService(BundleContext context) throws Exception {
        client = Utils.executeWithTCCLSwitch(H2JDBCClientProvider.class.getClassLoader(),
                () -> JDBCClient.createShared(vertx,
                        new JsonObject().put("provider_class", "io.vertx.ext.jdbc.spi.impl.HikariCPDataSourceProvider")
                                .put("jdbcUrl", "jdbc:h2:mem:test").put("driverClassName", "org.h2.Driver")
                                .put("maximumPoolSize", 30)));
        Dictionary<String, Object> properties = new Hashtable<>();
        properties.put("type", "h2");
        registration = context.registerService(JDBCClient.class, client, properties);
    }

    @Deactivate
    public void unregisterH2JDBCClientService() {
        registration.unregister();
        client.close();
    }
}
