/*
 * Copyright 2020 heyoulin.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.yofc.vertx.osgi;

import com.yofc.vertx.osgi.api.ProxyRoute;
import com.yofc.vertx.osgi.api.TriFunction;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.httpproxy.ProxyRequest;
import io.vertx.httpproxy.ProxyResponse;
import org.osgi.service.component.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;

/** @author heyoulin */
@Component(
    scope = ServiceScope.PROTOTYPE,
    service = {ProxyRoute.class})
public class ProxyRouteImpl implements ProxyRoute {

  private final Logger logger = LoggerFactory.getLogger(this.getClass());
  private String context = "";
  private String targetHost;
  private Long timeout;
  private boolean isSsl;
  private Integer targetPort;
  private HttpClient client;
  private Route route;
  private TriFunction<RoutingContext, HttpClientRequest, Buffer, Buffer> preListener;
  private TriFunction<RoutingContext, HttpServerResponse, Buffer, Buffer> postListener;

  @Reference private Vertx vertx;
  @Reference private Router router;

  public static String replaceDomainAndPort(
      String domain, String port, String url, boolean isSSL, boolean isRequest)
      throws MalformedURLException {
    StringBuilder urBak = new StringBuilder();
    URL tempUurl = new URL(url);
    String path = tempUurl.getPath();
    if (isRequest && isSSL) {
      urBak.append("https://");
    } else {
      urBak.append("http://");
    }
    urBak.append(domain);
    if (port != null && !"".equals(port)) {
      urBak.append(":" + port);
    }
    if (path != null && !"".equals(path)) {
      urBak.append(path);
    }

    return urBak.toString();
  }

  @Override
  public ProxyRoute configure(
      String routeUrl,
      String context,
      int order,
      boolean isSsl,
      String targetHost,
      Integer targetPort,
      Long timeout) {
    this.targetHost = targetHost;
    this.targetPort = targetPort;
    this.timeout = timeout;
    this.context = context;
    return configure(routeUrl, context, order, isSsl);
  }

  @Override
  public ProxyRoute configure(String routeUrl, String context, int order, boolean isSsl) {
    this.context = (context == null || "/".equals(context)) ? "" : context;
    if (!this.context.startsWith("/")) {
      this.context = "/" + this.context;
    }
    this.isSsl = isSsl;
    routeUrl = (routeUrl == null || "".equals(routeUrl) || "/".equals(routeUrl)) ? "/*" : routeUrl;
    if (!routeUrl.startsWith("/")) {
      routeUrl = "/" + routeUrl;
    }
    HttpClientOptions options = new HttpClientOptions().setLogActivity(true).setMaxPoolSize(100);
    if (isSsl) {
      options.setSsl(true).setTrustAll(true).setVerifyHost(false);
    }
    if (this.client != null) {
      this.client.close();
    }
    this.client = vertx.createHttpClient(options);
    if (this.route != null) {
      this.route.remove();
    }
    this.route = this.router.route(this.context + routeUrl);
    if (order >= 0) {
      this.route.order(order);
    }
    this.route.handler(this::doProxyRequest).failureHandler(this::doProxyRequestFailure);
    return this;
  }

  @Deactivate
  public void deactivate() {
    if (this.client != null) {
      this.client.close();
      this.client = null;
    }
    if (this.route != null) {
      this.route.remove();
      this.route = null;
    }
  }

  @Activate
  public void activate() {
    logger.info("ProxyRouteImpl active");
  }

  private void doProxyRequest(RoutingContext routingContext) {
    HttpServerRequest serverRequest = routingContext.request();
    ProxyRequest proxyReq = ProxyRequest.reverseProxy(serverRequest);
    HttpServerResponse serverResponse = routingContext.response();
    long timeoutToUse = this.timeout == null ? 2000L : this.timeout;
    int defaultPort = this.isSsl ? 443 : 80;
    int portToUse = (this.targetPort == null) ? defaultPort : this.targetPort;
    String url = serverRequest.uri();
    if (!"".equals(context) && url.startsWith(context)) {
      url = url.substring(context.length());
    }
    logger.debug("request:{}", url);
    final String targetUrl = url;
    RequestOptions requestOptions = new RequestOptions();
    requestOptions.setHost(targetHost);
    requestOptions.setPort(portToUse);
    requestOptions.setTimeout(timeoutToUse);

    client
        .request(requestOptions)
        .onSuccess(
            clientReq -> {
              proxyReq.setURI(targetUrl);
              proxyReq.bodyFilter(
                  buf -> {
                    if (preListener != null) {
                      buf.handler(buffer -> preListener.apply(routingContext, clientReq, buffer));
                    }
                    return buf;
                  });
              proxyReq.send(
                  clientReq,
                  ar -> {
                    if (ar.succeeded()) {
                      ProxyResponse resp = ar.result();
                      resp.bodyFilter(
                          buf -> {
                            if (postListener != null) {
                              buf.handler(
                                  buffer ->
                                      postListener.apply(routingContext, serverResponse, buffer));
                            }
                            return buf;
                          });
                      resp.send(h -> {});

                    } else {
                      if (!serverResponse.closed() && !serverResponse.ended()) {
                        serverResponse.end("request error:" + ar.cause());
                        serverResponse.end();
                      }
                    }
                  });
            })
        .onFailure(
            error -> {
              if (!serverResponse.closed() && !serverResponse.ended()) {
                serverResponse.end("request error:" + error.getMessage());
                serverResponse.end();
              }
            });
  }

  private void doProxyRequestFailure(RoutingContext failureroutingContext) {
    logger.debug("Matched route {}", failureroutingContext.currentRoute().getPath());
    failureroutingContext
        .response()
        .end(
            failureroutingContext.failure() == null
                ? "Unknow error"
                : failureroutingContext.failure().getMessage());
  }

  @Override
  public String getTargetHost() {
    return targetHost;
  }

  @Override
  public void setTargetHost(String targetHost) {
    this.targetHost = targetHost;
  }

  @Override
  public Long getTimeout() {
    return timeout;
  }

  @Override
  public void setTimeout(Long timeout) {
    this.timeout = timeout;
  }

  @Override
  public Integer getTargetPort() {
    return targetPort;
  }

  @Override
  public void setTargetPort(Integer targetPort) {
    this.targetPort = targetPort;
  }

  @Override
  public Route getRoute() {
    return this.route;
  }

  @Override
  public void addPostListener(
      TriFunction<RoutingContext, HttpServerResponse, Buffer, Buffer> listener) {
    this.postListener = listener;
  }

  @Override
  public void addPreListener(
      TriFunction<RoutingContext, HttpClientRequest, Buffer, Buffer> listener) {
    this.preListener = listener;
  }
}
