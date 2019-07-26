/*
 * Copyright 2017 Kislay Verma.
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

import java.io.File;
import java.util.Set;
import java.util.UUID;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ServiceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yofc.vertx.osgi.api.ProxyRoute;
import com.yofc.vertx.osgi.api.TriFunction;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.impl.FileUploadImpl;

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
    private String uploadsDir = "file-uploads";
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

    @SuppressWarnings("deprecation")
    private void doProxyRequest(RoutingContext routingContext) {
        HttpServerRequest serverRequest = routingContext.request();
        HttpServerResponse serverResponse = routingContext.response();
        String expect = serverRequest.getHeader("Expect");
        if (expect != null && expect.equalsIgnoreCase("100-Continue")) {
            serverRequest.response().writeContinue();
        }
        Buffer responseBuffer = Buffer.buffer();
        Buffer requestBuffer = Buffer.buffer();
        long timeoutToUse = this.timeout == null ? 1000L : this.timeout;
        int defaultPort = this.isSsl ? 443 : 80;
        int portToUse = (this.targetPort == null) ? defaultPort : this.targetPort;
        Set<FileUpload> fileUploads = routingContext.fileUploads();
        final String contentType = serverRequest.getHeader(HttpHeaders.CONTENT_TYPE);
        boolean isMultipart = false;
        boolean isUrlEncoded = false;
        if (contentType != null) {
            final String lowerCaseContentType = contentType.toLowerCase();
            isMultipart = lowerCaseContentType.startsWith(HttpHeaderValues.MULTIPART_FORM_DATA.toString());
            isUrlEncoded = lowerCaseContentType
                    .startsWith(HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED.toString());
        }

        if (isMultipart || isUrlEncoded) {
            serverRequest.setExpectMultipart(true);
            makeUploadDir(routingContext.vertx().fileSystem());
            serverRequest.uploadHandler(upload -> {
                String uploadedFileName = new File(uploadsDir, UUID.randomUUID().toString()).getPath();
                upload.streamToFileSystem(uploadedFileName);
                FileUploadImpl fileUpload = new FileUploadImpl(uploadedFileName, upload);
                fileUploads.add(fileUpload);
                upload.exceptionHandler(routingContext::fail);
            });
        }

        String url = serverRequest.uri();
        if (!context.equals("") && url.startsWith(context))
            url = url.substring(context.length());
        logger.debug("request:{}", url);
        HttpClientRequest clientRequest = client.request(serverRequest.method(), portToUse, targetHost, url)
                .setTimeout(timeoutToUse).setChunked(true);
        clientRequest.connectionHandler(connection -> {
            connection.exceptionHandler(ex -> {
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
            });
        });
        serverResponse.setChunked(true);
        serverRequest.handler(requestData -> {
            requestBuffer.appendBuffer(requestData);
        });
        serverRequest.headers().entries().forEach(entry -> {
            if (entry.getKey().equalsIgnoreCase("host"))
                clientRequest.putHeader(entry.getKey(), targetHost + (targetPort == 80 ? "" : ":" + targetPort));
            else if (entry.getKey().equalsIgnoreCase("referer")) {
                String referer = replaceDomainAndPort(this.targetHost, targetPort == 80 ? "" : "" + targetPort,
                        entry.getValue());
                clientRequest.putHeader(entry.getKey(), referer);
            } else
                clientRequest.putHeader(entry.getKey(), entry.getValue());
        });

        serverRequest.endHandler(v -> {

            try {
                if (preListener != null) {
                    Buffer returnBuffer = preListener.apply(routingContext, clientRequest, requestBuffer);
                    if (returnBuffer != null)
                        clientRequest.write(returnBuffer);
                    else
                        clientRequest.write(requestBuffer);
                } else
                    clientRequest.write(requestBuffer);
                clientRequest.end();
            } finally {
                deleteFileUploads(routingContext);
            }
        });

        serverRequest.exceptionHandler(routingContext::fail);
        clientRequest.handler(clientResponse -> {
            serverResponse.setStatusCode(clientResponse.statusCode());
            serverResponse.setStatusMessage(clientResponse.statusMessage());
            clientResponse.headers().entries().forEach(entry -> {
                serverResponse.putHeader(entry.getKey(), entry.getValue());
            });
            clientResponse.handler(responseData -> {
                responseBuffer.appendBuffer(responseData);
            });
            clientResponse.endHandler(v -> {
                if (postListener != null) {
                    Buffer returnBuffer = postListener.apply(routingContext, serverResponse, responseBuffer);
                    if (returnBuffer != null)
                        serverResponse.write(returnBuffer);
                    else
                        serverResponse.write(responseBuffer);
                } else
                    serverResponse.write(responseBuffer);
                if (!serverResponse.ended())
                    serverResponse.end();
            });
            clientResponse.exceptionHandler(error -> {
                serverResponse.write(error.getMessage());
            });
        });

        clientRequest.exceptionHandler(error -> {
            if (!serverResponse.closed() && !serverResponse.ended())
                serverResponse.end("request error:" + error.getMessage());
        });
    }

    public static String replaceDomainAndPort(String domain, String port, String url) {
        StringBuilder urBak = new StringBuilder();
        if (url.indexOf("//") != -1) {
            String[] splitTemp = url.split("//");
            urBak.append(splitTemp[0] + "//");
            if (port != null && !port.equals("")) {
                urBak.append(domain + ":" + port);
            } else {
                urBak.append(domain);
            }

            if (splitTemp.length >= 1 && splitTemp[1].indexOf('/') != -1) {
                String[] urlTemp2 = splitTemp[1].split("/");
                if (urlTemp2.length > 1) {
                    for (int i = 1; i < urlTemp2.length; i++) {
                        urBak.append("/" + urlTemp2[i]);
                    }
                }
            }
        }
        return urBak.toString();
    }

    private void makeUploadDir(FileSystem fileSystem) {
        if (!fileSystem.existsBlocking(uploadsDir)) {
            fileSystem.mkdirsBlocking(uploadsDir);
        }
    }

    private void deleteFileUploads(RoutingContext context) {
        for (FileUpload fileUpload : context.fileUploads()) {
            FileSystem fileSystem = context.vertx().fileSystem();
            String uploadedFileName = fileUpload.uploadedFileName();
            fileSystem.exists(uploadedFileName, existResult -> {
                if (existResult.failed()) {
                    logger.warn("Could not detect if uploaded file exists, not deleting: " + uploadedFileName,
                            existResult.cause());
                } else if (existResult.result()) {
                    fileSystem.delete(uploadedFileName, deleteResult -> {
                        if (deleteResult.failed()) {
                            logger.warn("Delete of uploaded file failed: " + uploadedFileName, deleteResult.cause());
                        }
                    });
                }
            });
        }
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
