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
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.RequestOptions;
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
    private boolean enableModifyBody = false;

    private TriFunction<RoutingContext, HttpClientRequest, Buffer, Buffer> preListener;
    private TriFunction<RoutingContext, HttpServerResponse, Buffer, Buffer> postListener;

    // =========================
    // 生命周期
    // =========================
    @Activate
    public void activate() {

        HttpClientOptions options = new HttpClientOptions().setKeepAlive(true).setConnectTimeout(timeout.intValue());

        if (isSsl) {
            options.setSsl(true).setTrustAll(true).setVerifyHost(false);
        }

        client = vertx.createHttpClient(options);

        proxy = HttpProxy.reverseProxy(client);

        // ✅ 只注册一次
        proxy.addInterceptor(new ProxyInterceptor() {

            @Override
            public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
                // 可选：这里只能做轻量逻辑
                return context.sendRequest();
            }
        });
    }

    @Deactivate
    public void deactivate() {
        if (route != null) {
            route.remove();
            route = null;
        }
    }

    // =========================
    // 配置
    // =========================
    @Override
    public ProxyRoute configure(String routeUrl, String context, int order, boolean isSsl, String targetHost,
            Integer targetPort, Long timeout, boolean enableModifyBody) {

        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.timeout = timeout;
        this.isSsl = isSsl;
        this.enableModifyBody = enableModifyBody;

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

        boolean needModify = needModify(ctx);

        // 🚨 禁止但触发
        if (needModify && !enableModifyBody) {
            logger.warn("Body modify required but disabled, uri={}", ctx.request().uri());
            ctx.response().setStatusCode(500).end("Body modify disabled");
            return;
        }

        // ✅ 走 custom（可改body）
        if (enableModifyBody && needModify) {
            handleCustom(ctx);
            return;
        }

        // ✅ 走 HttpProxy（高性能）
        proxy.origin(targetPort, targetHost);

        proxy.handle(ctx.request());
    }

    // =========================
    // 判断是否需要改body
    // =========================
    private boolean needModify(RoutingContext ctx) {

        HttpMethod method = ctx.request().method();

        if (method != HttpMethod.POST && method != HttpMethod.PUT && method != HttpMethod.PATCH) {
            return false;
        }

        String ct = ctx.request().getHeader("Content-Type");

        if (ct == null) {
            return false;
        }

        return ct.contains("application/json");
    }

    // =========================
    // custom代理（支持body修改）
    // =========================
    private void handleCustom(RoutingContext ctx) {

        HttpServerRequest req = ctx.request();
        HttpServerResponse resp = ctx.response();

        RequestOptions options = new RequestOptions().setHost(targetHost).setPort(targetPort).setURI(getUri(req.uri()))
                .setTimeout(timeout);

        client.request(options).onSuccess(clientReq -> {

            clientReq.setMethod(req.method());

            req.headers().forEach(h -> clientReq.putHeader(h.getKey(), h.getValue()));

            Buffer body = Buffer.buffer();

            req.handler(body::appendBuffer);

            req.endHandler(v -> {

                Buffer sendBuf = body;

                // ===== preListener =====
                if (preListener != null) {
                    sendBuf = preListener.apply(ctx, clientReq, body);
                }

                clientReq.send(sendBuf).onSuccess(clientResp -> {

                    resp.setStatusCode(clientResp.statusCode());
                    resp.headers().setAll(clientResp.headers());

                    Buffer respBuf = Buffer.buffer();

                    clientResp.handler(respBuf::appendBuffer);

                    clientResp.endHandler(v2 -> {

                        Buffer finalBuf = respBuf;

                        // ===== postListener =====
                        if (postListener != null) {
                            finalBuf = postListener.apply(ctx, resp, respBuf);
                        }

                        resp.end(finalBuf);
                    });

                }).onFailure(err -> fail(resp, err));

            });

        }).onFailure(err -> fail(resp, err));
    }

    private String getUri(String uri) {
        if (!context.isEmpty() && uri.startsWith(context)) {
            return uri.substring(context.length());
        }
        return uri;
    }

    private void fail(HttpServerResponse resp, Throwable err) {
        logger.error("proxy error", err);
        if (!resp.ended()) {
            resp.setStatusCode(502).end("Bad Gateway: " + err.getMessage());
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
}