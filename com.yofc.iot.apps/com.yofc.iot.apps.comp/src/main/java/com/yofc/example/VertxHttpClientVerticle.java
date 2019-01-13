package com.yofc.example;

import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Verticle;
import io.vertx.core.http.HttpClient;

/**
 * A component exposing itself as a verticle in the service registry, and creating a HTTP client to consume a web
 * page.
 */
@Component(immediate = true, service = { Verticle.class })
public class VertxHttpClientVerticle extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(VertxHttpClientVerticle.class);

    @Override
    public void start() throws Exception {
        HttpClient client = vertx.createHttpClient();
        client.get("perdu.com", "/").handler(response -> {
            response.bodyHandler(buffer -> logger.info(buffer.toString("UTF-8")));
        });
    }
}
