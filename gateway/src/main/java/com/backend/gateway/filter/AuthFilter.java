package com.backend.gateway.filter;

import com.backend.gateway.config.BaseAuthConfig;
import filter.TraceIdFilter; // 导入 TraceIdWebFilter 以获取 Context Key
import lombok.extern.slf4j.Slf4j;
import network.HttpResponseUtils; // 导入统一响应工具类
import network.TraceIdUtils; // 导入 MDC/TraceId 实用工具
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import reactor.core.publisher.Mono;
import security.AuthValidationUtils;
import webflux.WebExchangeUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/**
 * 授权过滤器工厂
 * 负责读取配置、创建 GatewayFilter 实例，并执行异步授权校验。
 */
@Slf4j
public abstract class AuthFilter<T extends BaseAuthConfig> extends AbstractGatewayFilterFactory<T> {

    private final ExecutorService executorService;
    private final CacheManager cacheManager;

    public AuthFilter(Class<T> configClass, ExecutorService executorService, CacheManager cacheManager) {
        super(configClass);
        // 使用 TraceIdUtils.mdcExecutor 确保异步线程池支持 MDC
        this.executorService = TraceIdUtils.mdcExecutor(executorService);
        this.cacheManager = cacheManager;
    }

    protected abstract String getSecret();

    /** 检查是否为无需授权的请求方法 */
    protected boolean authorizedRequest(String method) {
        return false;
    }

    public GatewayFilter apply(T config) {
        if (!config.isEnabled()) {
            // 如果禁用，返回一个不执行任何操作的过滤器
            return (exchange, chain) -> chain.filter(exchange);
        }

        // 【结构优化点】：将复杂的过滤逻辑抽离到私有方法中
        return createAuthGatewayFilter(config);
    }

    /**
     * 核心实现：构建并返回实际的 GatewayFilter 实例
     */
    private GatewayFilter createAuthGatewayFilter(T config) {

        // 实际的过滤逻辑，包含了异步和 Reactor Context 处理
        return (exchange, chain) -> Mono.deferContextual(contextView -> {

            // 1. 从 Reactor Context 中获取 Trace ID (由 TraceIdWebFilter 注入)
            final String traceId = contextView
                    .getOrEmpty(TraceIdFilter.CONTEXT_KEY_TRACE_ID)
                    .map(Object::toString)
                    .orElse("NO_TRACE_ID");

            // 提前获取 Exchange 的核心信息
            final String method = WebExchangeUtils.getMethod(exchange);
            final String path = WebExchangeUtils.getPath(exchange);
            final String routeId = WebExchangeUtils.getRouteId(exchange);
            final String ip = WebExchangeUtils.getClientIp(exchange);

            // 2. 异步验证 Supplier：MDC 仅用于此 Supplier 内部的日志记录
            Supplier<Boolean> validationSupplier = () -> {
                TraceIdUtils.setTraceId(traceId); // 异步线程开始时设置 MDC
                try {
                    String cacheKey = "auth:" + ip + ":" + method + ":" + path;
                    Cache cache = cacheManager.getCache("authCache");
                    Boolean cached = cache != null ? cache.get(cacheKey, Boolean.class) : null;

                    if (cached != null) {
                        log.info("[traceId={}] 🔹 Cache hit | result={}| key={} ", traceId, cached, cacheKey);
                        return cached;
                    }

                    boolean authorized = AuthValidationUtils.isAuthorized(exchange, getSecret());
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
                    TraceIdUtils.clearMdc(); // 异步线程结束时清除 MDC
                }
            };

            // 3. 异步执行并返回 Mono
            CompletableFuture<Boolean> validationFuture = CompletableFuture
                    .supplyAsync(validationSupplier, executorService);

            return Mono.fromFuture(validationFuture)
                    .flatMap(authorized -> {
                        if (!authorized) {
                            TraceIdUtils.setTraceId(traceId);
                            log.warn("[traceId={}] ❌ Request blocked | IP={} | Route={} | Method={} | Path={}",
                                    traceId, ip, routeId, method, path);
                            TraceIdUtils.clearMdc();
                            // 【修正点 A】：传递 ServerWebExchange exchange
                            return HttpResponseUtils.write(exchange,
                                    HttpResponseUtils.unauthorized("Unauthorized or service unavailable"));
                        }
                        // 验证成功，继续执行过滤器链
                        return chain.filter(exchange);
                    })
                    // 确保在主线程日志记录或错误恢复时 Trace ID 仍可用
                    .onErrorResume(ex -> {
                        TraceIdUtils.setTraceId(traceId);
                        log.error("[traceId={}] ❌ Downstream unavailable | IP={} | Route={} | Method={} | Path={} | Exception={}",
                                traceId, ip, routeId, method, path, ex.toString());
                        TraceIdUtils.clearMdc();
                        // 【修正点 B】：传递 ServerWebExchange exchange
                        return HttpResponseUtils.write(exchange,
                                HttpResponseUtils.internalError("Downstream service unavailable"));
                    });
        });
    }
}