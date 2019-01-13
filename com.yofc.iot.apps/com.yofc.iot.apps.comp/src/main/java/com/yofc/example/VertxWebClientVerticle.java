package com.yofc.example;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Verticle;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.http.HttpClient;

/**
 * A component exposing itself as a verticle in the service registry, and creating a Web client to consume a web
 * page.
 */
@Component(immediate = true, service = { Verticle.class })
public class VertxWebClientVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(VertxWebClientVerticle.class);

    @Override
    public void start() throws Exception {
        // WebClient client = WebClient.create(vertx);
        HttpClient client = vertx.createHttpClient();
        client.get(80, "perdu.com", "/").toObservable().subscribe(s -> {
            s.bodyHandler(b -> LOGGER.info("From web client:{} ", b.toString()));

        }, Throwable::printStackTrace);
    }

    @Activate
    public void active() {
        System.out.println("start");
    }

    @Deactivate
    public void unregisterJDBCClientService() {
        System.out.println("stop");
    }
}
