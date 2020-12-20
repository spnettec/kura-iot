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

import java.net.MalformedURLException;
import java.net.URL;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ServiceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yofc.vertx.osgi.api.ProxyRoute;
import com.yofc.vertx.osgi.api.TriFunction;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.RequestOptions;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 *
 * @author heyoulin
 */
@Component(scope = ServiceScope.PROTOTYPE, service = { ProxyRoute.class })
public class ProxyRouteImpl implements ProxyRoute {

    private String context = "";
    private String targetHost;
    private Long timeout;
    private boolean isSsl;
    private Integer targetPort;
    private HttpClient client;
    private Route route;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private TriFunction<RoutingContext, HttpClientRequest, Buffer, Buffer> preListener;
    private TriFunction<RoutingContext, HttpServerResponse, Buffer, Buffer> postListener;

    @Reference
    private Vertx vertx;
    @Reference
    private Router router;

    public ProxyRoute configure(String routeUrl, String context, int order, boolean isSsl, String targetHost,
            Integer targetPort, Long timeout) {
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.timeout = timeout;
        this.context = context;
        return configure(routeUrl, context, order, isSsl);
    }

    public ProxyRoute configure(String routeUrl, String context, int order, boolean isSsl) {
        this.context = (context == null || context.equals("/")) ? "" : context;
        this.isSsl = isSsl;
        routeUrl = (routeUrl == null || routeUrl.equals("") || routeUrl.equals("/")) ? "/*" : routeUrl;
        HttpClientOptions options = new HttpClientOptions().setLogActivity(true).setMaxPoolSize(100);
        if (isSsl)
            options.setSsl(true).setTrustAll(true).setVerifyHost(false);
        if (this.client != null)
            this.client.close();
        this.client = vertx.createHttpClient(options);
        if (this.route != null)
            this.route.remove();
        this.route = this.router.route(this.context + routeUrl);
        if (order >= 0)
            this.route.order(order);
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
        HttpServerResponse serverResponse = routingContext.response();
        String expect = serverRequest.getHeader("Expect");
        if (expect != null && expect.equalsIgnoreCase("100-Continue")) {
            serverRequest.response().writeContinue();
        }
        Buffer responseBuffer = Buffer.buffer();
        Buffer requestBuffer = Buffer.buffer();
        long timeoutToUse = this.timeout == null ? 2000L : this.timeout;
        int defaultPort = this.isSsl ? 443 : 80;
        int portToUse = (this.targetPort == null) ? defaultPort : this.targetPort;
        String url = serverRequest.uri();
        if (!context.equals("") && url.startsWith(context))
            url = url.substring(context.length());
        logger.debug("request:{}", url);
        final String targetUrl = url;
        RequestOptions requestOptions = new RequestOptions();
        requestOptions.setHost(targetHost);
        requestOptions.setPort(portToUse);
        requestOptions.setTimeout(timeoutToUse);

        client.connectionHandler(connection -> connection.exceptionHandler(ex -> {
            String errMsg = ex.getMessage();
            if (errMsg == null) {
                Throwable cause = ex.getCause();
                if (cause == null)
                    errMsg = ex.toString();
                else
                    errMsg = ex.getCause().toString();
            }
            if (!serverResponse.closed() && !serverResponse.ended())
                serverResponse.end("request error:" + errMsg);
        }));

        serverRequest.exceptionHandler(routingContext::fail);

        client.request(requestOptions).onSuccess(clientRequest -> {
            serverRequest.resume();
            serverRequest.handler(requestData -> {
                // clientRequest.write(requestData);
                requestBuffer.appendBuffer(requestData);
            });
            serverRequest.endHandler(v -> {
                if (preListener != null)
                    preListener.apply(routingContext, clientRequest, requestBuffer);
                clientRequest.end(requestBuffer);

            });
            final String hostString = serverRequest.getHeader("Host");

            serverRequest.headers().entries().forEach(entry -> {
                if (entry.getKey().equalsIgnoreCase("host")) {
                    requestOptions.putHeader(entry.getKey(), targetHost + (targetPort == 80 ? "" : ":" + targetPort));
                } else if (entry.getKey().equalsIgnoreCase("transfer-encoding")) {
                    if (entry.getValue() != null && entry.getValue().equals("chunked"))
                        clientRequest.setChunked(true);
                    clientRequest.putHeader(entry.getKey(), entry.getValue());
                } else if (entry.getKey().equalsIgnoreCase("referer")) {
                    String referer = entry.getValue();
                    try {
                        referer = replaceDomainAndPort(this.targetHost, targetPort == 80 ? "" : "" + targetPort,
                                entry.getValue(), this.isSsl, true);
                    } catch (MalformedURLException e) {

                    }
                    clientRequest.putHeader(entry.getKey(), referer);
                } else
                    clientRequest.putHeader(entry.getKey(), entry.getValue());
            });
            clientRequest.setHost(targetHost);
            clientRequest.setPort(portToUse);
            clientRequest.setTimeout(timeoutToUse);
            clientRequest.setMethod(serverRequest.method());
            clientRequest.setURI(targetUrl);
            clientRequest.response().onComplete(res -> {
                if (res.failed()) {
                    responseBuffer.appendString(res.cause().getMessage());
                    if (postListener != null) {
                        postListener.apply(routingContext, serverResponse, responseBuffer);
                    }
                    if (!serverResponse.closed() && !serverResponse.ended()) {
                        serverResponse.write(responseBuffer);
                        serverResponse.end();
                    }
                }
                HttpClientResponse clientResponse = res.result();
                serverResponse.setStatusCode(clientResponse.statusCode());
                serverResponse.setStatusMessage(clientResponse.statusMessage());
                clientResponse.headers().entries().forEach(entry -> {
                    if (entry.getKey().equalsIgnoreCase("host")) {
                        serverResponse.putHeader(entry.getKey(), hostString);
                    } else if (entry.getKey().equalsIgnoreCase("transfer-encoding")) {
                        if (entry.getValue() != null && entry.getValue().equals("chunked"))
                            serverResponse.setChunked(true);
                        serverResponse.putHeader(entry.getKey(), entry.getValue());
                    } else if (entry.getKey().equalsIgnoreCase("location")) {
                        String value = entry.getValue();
                        try {
                            String[] hostStrings = hostString.split(":");
                            String rawHost = hostStrings[0];
                            String rawPort = "80";
                            if (hostString.length() > 1) {
                                rawPort = hostStrings[1];
                            } else if (this.isSsl)
                                rawPort = "443";
                            value = replaceDomainAndPort(rawHost, rawPort, entry.getValue() + context, this.isSsl,
                                    false);
                        } catch (MalformedURLException e) {

                        }
                        serverResponse.putHeader(entry.getKey(), value);
                    } else
                        serverResponse.putHeader(entry.getKey(), entry.getValue());
                });
                clientResponse.handler(responseData -> {
                    // serverResponse.write(responseData);
                    responseBuffer.appendBuffer(responseData);
                });
                clientResponse.endHandler(v -> {
                    if (postListener != null)
                        postListener.apply(routingContext, serverResponse, responseBuffer);
                    if (!serverResponse.closed() && !serverResponse.ended()) {
                        serverResponse.write(responseBuffer);
                        serverResponse.end();
                    }
                });
            }).onFailure(error -> {
                serverRequest.resume();
                if (!serverResponse.closed() && !serverResponse.ended()) {
                    serverResponse.write(error.getMessage());
                    serverResponse.end();
                }
            });

        }).onFailure(error -> {
            serverRequest.resume();
            if (!serverResponse.closed() && !serverResponse.ended()) {
                serverResponse.end("request error:" + error.getMessage());
                serverResponse.end();
            }
        });
        serverRequest.pause();
    }

    public static String replaceDomainAndPort(String domain, String port, String url, boolean isSSL, boolean isRequest)
            throws MalformedURLException {
        StringBuilder urBak = new StringBuilder();
        URL tempUurl = new URL(url);
        String path = tempUurl.getPath();
        if (isRequest && isSSL)
            urBak.append("https://");
        else
            urBak.append("http://");
        urBak.append(domain);
        if (port != null && !port.equals(""))
            urBak.append(":" + port);
        if (path != null && !path.equals(""))
            urBak.append(path);

        return urBak.toString();
    }

    private void doProxyRequestFailure(RoutingContext failureroutingContext) {
        logger.debug("Matched route {}", failureroutingContext.currentRoute().getPath());
        failureroutingContext.response().end(failureroutingContext.failure() == null ? "Unknow error"
                : failureroutingContext.failure().getMessage());
    }

    public String getTargetHost() {
        return targetHost;
    }

    public void setTargetHost(String targetHost) {
        this.targetHost = targetHost;
    }

    public Long getTimeout() {
        return timeout;
    }

    public void setTimeout(Long timeout) {
        this.timeout = timeout;
    }

    public Integer getTargetPort() {
        return targetPort;
    }

    public void setTargetPort(Integer targetPort) {
        this.targetPort = targetPort;
    }

    public Route getRoute() {
        return this.route;
    }

    @Override
    public void addPostListener(TriFunction<RoutingContext, HttpServerResponse, Buffer, Buffer> listener) {
        this.postListener = listener;

    }

    @Override
    public void addPreListener(TriFunction<RoutingContext, HttpClientRequest, Buffer, Buffer> listener) {
        this.preListener = listener;

    }

}
