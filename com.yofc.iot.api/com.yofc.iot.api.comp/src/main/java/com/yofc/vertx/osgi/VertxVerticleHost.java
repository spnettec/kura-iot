package com.yofc.vertx.osgi;

import java.util.concurrent.ConcurrentHashMap;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.ThreadingModel;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;

/** A component consuming all verticles exposed in the service registry and deploying them. */
@Component(immediate = true)
public class VertxVerticleHost {

    private static final Logger logger = LoggerFactory.getLogger(VertxVerticleHost.class);
    ConcurrentHashMap<Verticle, String> deploymentIds = new ConcurrentHashMap<>();
    @Reference
    private Vertx vertx;

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC, unbind = "unbindVerticle")
    public void bindVerticle(Verticle verticle) {
        logger.info("Deploying verticle {}", verticle);
        this.vertx.deployVerticle(verticle, new DeploymentOptions().setThreadingModel(ThreadingModel.VIRTUAL_THREAD))
                .andThen(ar -> {
                    if (ar.succeeded()) {
                        deploymentIds.put(verticle, ar.result());
                    } else {
                        logger.error("Cannot deploy {}" + verticle, ar.cause());
                    }
                });
    }

    public void unbindVerticle(Verticle verticle) {
        String id = deploymentIds.get(verticle);
        logger.info("Undeploying verticle ({} {})", verticle, id);
        if (id != null) {
            this.vertx.undeploy(id);
        }
    }
}
