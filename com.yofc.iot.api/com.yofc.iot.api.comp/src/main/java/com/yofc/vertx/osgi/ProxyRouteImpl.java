package com.yofc.vertx.osgi;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ServiceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yofc.vertx.osgi.api.ProxyRoute;
import com.yofc.vertx.osgi.api.TriFunction;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.httpproxy.HttpProxy;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.ProxyResponse;
import lombok.Getter;
import lombok.Setter;

@Component(scope = ServiceScope.PROTOTYPE, service = { ProxyRoute.class })
public class ProxyRouteImpl implements ProxyRoute {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Reference
    private Vertx vertx;

    @Reference
    private Router router;

    private HttpClient client;
    private HttpProxy proxy;
    private Route route;
    private Boolean clientSsl;
    private Long clientTimeout;

    @Getter
    @Setter
    private String targetHost;

    @Getter
    @Setter
    private Integer targetPort;

    @Getter
    @Setter
    private Long timeout = 2000L;

    private boolean isSsl;
    private String context = "";
    private boolean enableAccessLog = false;

    private TriFunction<RoutingContext, HttpClientRequest, Buffer, Buffer> preListener;
    private TriFunction<RoutingContext, HttpServerResponse, Buffer, Buffer> postListener;

    // =========================
    // 生命周期
    // =========================
    @Activate
    public void activate() {
        // The proxy client is created lazily after configure() has applied SSL
        // and timeout settings.
    }

    @Deactivate
    public void deactivate() {
        if (route != null) {
            route.remove();
            route = null;
        }
        closeClient();
    }

    // =========================
    // 配置
    // =========================
    @Override
    public ProxyRoute configure(String routeUrl, String context, int order, boolean isSsl, String targetHost,
            Integer targetPort, Long timeout, boolean enableLog) {

        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.timeout = timeout == null ? 2000L : timeout;
        this.isSsl = isSsl;
        this.enableAccessLog = enableLog;

        if (client != null && (!Boolean.valueOf(isSsl).equals(clientSsl) || !this.timeout.equals(clientTimeout))) {
            closeClient();
        }

        return configure(routeUrl, context, order, isSsl);
    }

    @Override
    public ProxyRoute configure(String routeUrl, String context, int order, boolean isSsl) {

        this.context = (context == null || "/".equals(context)) ? "" : context;

        if (!this.context.isEmpty() && !this.context.startsWith("/")) {
            this.context = "/" + this.context;
        }

        routeUrl = (routeUrl == null || routeUrl.isEmpty() || "/".equals(routeUrl)) ? "/*" : routeUrl;

        if (route != null) {
            route.remove();
        }

        route = router.route(this.context + routeUrl);

        if (order >= 0) {
            route.order(order);
        }

        route.handler(this::handle);

        return this;
    }

    // =========================
    // 核心入口
    // =========================
    private void handle(RoutingContext ctx) {
        ensureProxy();
        proxy.origin(targetPort, targetHost);
        proxy.handle(ctx.request());
    }

    private synchronized void ensureProxy() {
        if (client != null && proxy != null) {
            return;
        }

        HttpClientOptions options = new HttpClientOptions().setKeepAlive(true).setConnectTimeout(timeout.intValue());

        if (isSsl) {
            options.setSsl(true).setTrustAll(true).setVerifyHost(false);
        }

        client = vertx.createHttpClient(options);
        clientSsl = isSsl;
        clientTimeout = timeout;
        proxy = HttpProxy.reverseProxy(client);
        proxy.addInterceptor(new ProxyInterceptor() {

            @Override
            public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
                context.set("proxyStartTime", System.nanoTime());
                emitRequestLog(context);
                return context.sendRequest();
            }

            @Override
            public Future<Void> handleProxyResponse(ProxyContext context) {
                emitResponseLog(context);
                return context.sendResponse();
            }
        });
    }

    private synchronized void closeClient() {
        HttpClient oldClient = client;
        client = null;
        proxy = null;
        clientSsl = null;
        clientTimeout = null;

        if (oldClient != null) {
            oldClient.close().onFailure(err -> logger.warn("Failed to close proxy http client", err));
        }
    }

    // =========================
    // listener → interceptor
    // =========================
    @Override
    public void addPreListener(TriFunction<RoutingContext, HttpClientRequest, Buffer, Buffer> listener) {
        this.preListener = listener;
    }

    @Override
    public void addPostListener(TriFunction<RoutingContext, HttpServerResponse, Buffer, Buffer> listener) {
        this.postListener = listener;
    }

    @Override
    public Route getRoute() {
        return route;
    }

    private void emitRequestLog(ProxyContext context) {
        if (!enableAccessLog) {
            return;
        }

        try {
            String log = proxyLog("request", context.request().getMethod().name(), context.request().getURI(), null,
                    null);
            if (preListener != null) {
                preListener.apply(null, null, Buffer.buffer(log));
            }
        } catch (Exception e) {
            logger.warn("Failed to emit proxy request log", e);
        }
    }

    private void emitResponseLog(ProxyContext context) {
        if (!enableAccessLog || context.response() == null) {
            return;
        }

        Long startTime = context.get("proxyStartTime", Long.class);
        Long elapsedMillis = startTime == null ? null : (System.nanoTime() - startTime) / 1_000_000L;
        try {
            String log = proxyLog("response", context.request().getMethod().name(), context.request().getURI(),
                    context.response().getStatusCode(), elapsedMillis);
            if (postListener != null) {
                postListener.apply(null, null, Buffer.buffer(log));
            }
        } catch (Exception e) {
            logger.warn("Failed to emit proxy response log", e);
        }
    }

    private String proxyLog(String phase, String method, String uri, Integer status, Long elapsedMillis) {
        StringBuilder result = new StringBuilder(160);
        result.append('{');
        appendJsonField(result, "phase", phase);
        result.append(',');
        appendJsonField(result, "method", method);
        result.append(',');
        appendJsonField(result, "uri", uri);
        result.append(',');
        appendJsonField(result, "targetHost", targetHost);
        result.append(",\"targetPort\":").append(targetPort);
        if (status != null) {
            result.append(",\"status\":").append(status);
        }
        if (elapsedMillis != null) {
            result.append(",\"elapsedMillis\":").append(elapsedMillis);
        }
        result.append('}');
        return result.toString();
    }

    private void appendJsonField(StringBuilder result, String key, String value) {
        result.append('"').append(key).append("\":");
        appendJsonString(result, value);
    }

    private void appendJsonString(StringBuilder result, String value) {
        if (value == null) {
            result.append("null");
            return;
        }

        result.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
            case '\\':
                result.append("\\\\");
                break;
            case '"':
                result.append("\\\"");
                break;
            case '\n':
                result.append("\\n");
                break;
            case '\r':
                result.append("\\r");
                break;
            case '\t':
                result.append("\\t");
                break;
            default:
                result.append(c);
                break;
            }
        }
        result.append('"');
    }
}
