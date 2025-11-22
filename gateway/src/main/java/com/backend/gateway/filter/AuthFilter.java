package com.backend.gateway.filter;

import com.backend.gateway.config.BaseAuthConfig;
import lombok.extern.slf4j.Slf4j;
import network.ClientHeaderUtils;
import network.HttpResponseUtils;
import network.TraceIdUtils;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import security.AuthValidationUtils;
import webflux.ExchangeUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

@Slf4j
public abstract class AuthFilter<T extends BaseAuthConfig> extends AbstractGatewayFilterFactory<T> {

    private final ExecutorService executorService;
    private final CacheManager cacheManager;

    public AuthFilter(Class<T> configClass, ExecutorService executorService, CacheManager cacheManager) {
        super(configClass);
        this.executorService = TraceIdUtils.mdcExecutor(executorService);
        this.cacheManager = cacheManager;
    }

    protected abstract String getSecret();

    protected boolean authorizedRequest(String method) {
        return false;
    }

    @Override
    public GatewayFilter apply(T config) {
        return (exchange, chain) -> {
            if (!config.isEnabled()) return chain.filter(exchange);

            String method = ExchangeUtils.getMethod(exchange);
            String path = ExchangeUtils.getPath(exchange);
            String routeId = ExchangeUtils.getRouteId(exchange);
            String ip = ClientHeaderUtils.getClientIp(exchange);
            ServerHttpResponse response = ExchangeUtils.getResponse(exchange);

            // 获取 traceId 并设置 MDC，同时传递给下游
            ServerWebExchange mutatedExchange = TraceIdUtils.enrichExchange(exchange);
            final String traceId = TraceIdUtils.getTraceId(mutatedExchange);

            // 异步验证 Supplier
            Supplier<Boolean> validationSupplier = TraceIdUtils.wrapSupplier(() -> {
                TraceIdUtils.setTraceId(traceId);
                try {
                    String cacheKey = "auth:" + ip + ":" + method + ":" + path;
                    Cache cache = cacheManager.getCache("authCache");
                    Boolean cached = cache != null ? cache.get(cacheKey, Boolean.class) : null;

                    if (cached != null) {
                        log.info("[traceId={}] 🔹 Cache hit | result={}| key={} ", traceId, cached, cacheKey);
                        return cached;
                    }

                    boolean authorized = AuthValidationUtils.isAuthorized(mutatedExchange, getSecret());
                    if (!authorized || authorizedRequest(method)) {
                        log.warn("[traceId={}] ❌ Unauthorized or method not allowed | IP={} | Route={} | Method={} | Path={}",
                                traceId, ip, routeId, method, path);
                        if (cache != null) cache.put(cacheKey, false);
                        return false;
                    }

                    log.info("[traceId={}] ✅ Authorized request | IP={} | Route={} | Method={} | Path={}",
                            traceId, ip, routeId, method, path);
                    if (cache != null) cache.put(cacheKey, true);
                    return true;
                } finally {
                    TraceIdUtils.clearMdc();
                }
            });

            // 异步执行并返回 Mono
            CompletableFuture<Boolean> validationFuture = CompletableFuture
                    .supplyAsync(validationSupplier, executorService);

            return Mono.fromFuture(validationFuture)
                    .flatMap(authorized -> {
                        if (!authorized) {
                            TraceIdUtils.setTraceId(traceId);
                            log.warn("[traceId={}] ❌ Request blocked | IP={} | Route={} | Method={} | Path={}",
                                    traceId, ip, routeId, method, path);
                            return HttpResponseUtils.write(response,
                                    HttpResponseUtils.internalError("Unauthorized or service unavailable"));
                        }
                        return chain.filter(mutatedExchange)
                                .contextWrite(ctx -> ctx.put("traceId", traceId));
                    })
                    .doOnEach(signal -> signal.getContextView()
                            .getOrEmpty("traceId")
                            .ifPresent(t -> TraceIdUtils.setTraceId(t.toString()))
                    )
                    .doFinally(sig -> TraceIdUtils.clearMdc())
                    .onErrorResume(ex -> {
                        TraceIdUtils.setTraceId(traceId);
                        log.error("[traceId={}] ❌ Downstream unavailable | IP={} | Route={} | Method={} | Path={} | Exception={}",
                                traceId, ip, routeId, method, path, ex.toString());
                        TraceIdUtils.clearMdc();
                        return HttpResponseUtils.write(response,
                                HttpResponseUtils.internalError("Downstream service unavailable"));
                    });
        };
    }
}
