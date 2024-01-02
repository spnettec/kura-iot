package com.yofc.opc.impl;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.apache.plc4x.java.DefaultPlcDriverManager;
import org.apache.plc4x.java.api.PlcConnectionManager;
import org.apache.plc4x.java.utils.cache.CachedPlcConnectionManager;
import org.eclipse.kura.KuraException;
import org.eclipse.kura.configuration.ConfigurableComponent;
import org.osgi.framework.BundleContext;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yofc.opc.impl.OPCUAServerServiceImpl.Config;
import com.yofc.opcuaserver.OPCUAServer;

@Designate(ocd = Config.class, factory = true)
@Component(configurationPolicy = ConfigurationPolicy.REQUIRE, immediate = true)
public class OPCUAServerServiceImpl implements ConfigurableComponent {

    private static final Logger logger = LoggerFactory.getLogger(OPCUAServerServiceImpl.class);
    private Optional<CompletableFuture<OPCUAServer>> startTask = Optional.empty();
    private Config preConfig = null;

    @Reference
    private PlcConnectionManager plcConnectionManager;

    @Activate
    public void start(final BundleContext context, Config properties) throws KuraException {
        if (plcConnectionManager == null) {
            ClassLoader classloader = context.getBundle().adapt(BundleWiring.class).getClassLoader();
            plcConnectionManager = CachedPlcConnectionManager.getBuilder(new DefaultPlcDriverManager(classloader))
                    .build();
        }
        this.preConfig = properties;
        updateConfig(properties);
    }

    @Modified
    public void update(Config properties) throws KuraException {
        if (this.preConfig != null && properties.toString().equals(this.preConfig.toString())) {
            return;
        }
        this.preConfig = properties;
        closeServer();
        updateConfig(properties);

    }

    @Deactivate
    public void stop() {
        closeServer();

    }

    private void updateConfig(Config properties) throws KuraException {
        if (!properties.activate()) {
            closeServer();
            return;
        }
        this.startTask = Optional.of(doStart(properties));
    }

    private synchronized CompletableFuture<OPCUAServer> doStart(Config properties) throws KuraException {
        if (this.startTask.isPresent() && !this.startTask.get().isDone()) {
            return this.startTask.get();
        }

        try {
            return new OPCUAServer(properties.serverConfig(), false, plcConnectionManager).startup();
        } catch (Exception e) {
            throw KuraException.internalError(e);
        }
    }

    private void closeServer() {

        if (this.startTask.isPresent()) {
            try {
                this.startTask.get().get().shutdown().get();
            } catch (InterruptedException | ExecutionException e) {
                logger.error("shutDown server error", e);
                Thread.currentThread().interrupt();
            } finally {
                this.startTask = Optional.empty();
            }
        }
    }

    @ObjectClassDefinition(id = "com.yofc.opc.impl.OPCUAServerServiceImpl", name = "%name", description = "%description", localization = "OSGI-INF/l10n/com.yofc.opc.impl.OPCUAServerServiceCfg")
    @interface Config {

        @AttributeDefinition(name = "%activate", description = "%activateDesc")
        boolean activate()

        default false;

        @AttributeDefinition(name = "%serverConfig", description = "%serverConfigDesc")
        String serverConfig() default "";
    }

}
