package com.yofc.vertx.osgi.api;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.RoutingContext;

public interface ProxyRoute {

    ProxyRoute configure(String routeUrl, String context, int order, boolean isSsl, String targetHost,
            Integer targetPort, Long timeout);

    ProxyRoute configure(String routeUrl, String context, int order, boolean isSsl);

    void addPostListener(TriFunction<RoutingContext, HttpServerResponse, Buffer, Buffer> listener);

    void addPreListener(TriFunction<RoutingContext, HttpClientRequest, Buffer, Buffer> lister);

    String getTargetHost();

    void setTargetHost(String targetHost);

    Long getTimeout();

    void setTimeout(Long timeout);

    Integer getTargetPort();

    void setTargetPort(Integer targetPort);

    Route getRoute();
}
