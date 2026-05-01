package com.backend.gateway.filter;

import com.backend.gateway.config.BaseAuthConfig;
import filter.TraceIdFilter;
import lombok.extern.slf4j.Slf4j;
import network.HttpResponseUtils;
import network.TraceIdUtils;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler; // 🌟 导入 Scheduler
import security.AuthValidationUtils;
import webflux.WebExchangeUtils;

import java.util.concurrent.Callable; // 引入 Callable 用于 Mono.fromCallable
import java.util.concurrent.ExecutorService; // 仍然保留，但不再是核心依赖
import java.util.concurrent.CompletableFuture; // 移除

/**
 * 授权过滤器工厂
 * 负责读取配置、创建 GatewayFilter 实例，并执行异步授权校验。
 */
@Slf4j
public abstract class AuthFilter<T extends BaseAuthConfig> extends AbstractGatewayFilterFactory<T> {

    // 🌟 核心修改 1: 将 ExecutorService 替换为 Scheduler
    private final Scheduler scheduler;
    private final CacheManager cacheManager;

    // 🌟 核心修改 2: 构造器接受 Scheduler
    public AuthFilter(Class<T> configClass, Scheduler scheduler, CacheManager cacheManager) {
        super(configClass);
        // 不再需要 TraceIdUtils.mdcExecutor，因为我们将使用 Reactor Context 传播
        this.scheduler = scheduler;
        this.cacheManager = cacheManager;
    }

    protected abstract String getSecret();

    /** 检查是否为无需授权的请求方法 */
    protected boolean authorizedRequest(String method) {
        return false;
    }

    /** 白名单路径，子类可覆盖 */
    protected boolean isWhitelistedPath(String path) {
        return false;
    }

    public GatewayFilter apply(T config) {
        if (!config.isEnabled()) {
            return (exchange, chain) -> chain.filter(exchange);
        }
        return createAuthGatewayFilter(config);
    }

    /**
     * 核心实现：构建并返回实际的 GatewayFilter 实例
     */
    private GatewayFilter createAuthGatewayFilter(T config) {

        return (exchange, chain) -> Mono.deferContextual(contextView -> {

            // 1. 从 Reactor Context 中获取 Trace ID (由 TraceIdWebFilter 注入)
            final String traceId = contextView
                    .getOrEmpty(TraceIdFilter.CONTEXT_KEY_TRACE_ID)
                    .map(Object::toString)
                    .orElse("NO_TRACE_ID");

            // 🌟 [可选优化] 在主线程设置 MDC，确保后续日志能打印 Trace ID
            if (!"NO_TRACE_ID".equals(traceId)) {
                TraceIdUtils.setTraceId(traceId);
            }
            // ❗ 注意：这里不需要清 MDC，因为这是 I/O 线程，留给 doFinally/框架清理

            final String method = WebExchangeUtils.getMethod(exchange);
            final String path = WebExchangeUtils.getPath(exchange);
            final String routeId = WebExchangeUtils.getRouteId(exchange);
            final String ip = WebExchangeUtils.getClientIp(exchange);

            // Check path whitelist first
            if (isWhitelistedPath(path)) {
                log.info("[traceId={}] ✅ Whitelisted path | IP={} | Route={} | Method={} | Path={}",
                        traceId, ip, routeId, method, path);
                return chain.filter(exchange);
            }

            // 2. 阻塞验证 Callable：用于 Mono.fromCallable()，包含所有阻塞逻辑和 MDC 切换
            Callable<Boolean> validationCallable = () -> {
                TraceIdUtils.setTraceId(traceId); // 异步线程开始时设置 MDC
                try {
                    String cacheKey = "auth:" + ip + ":" + method + ":" + path;
                    Cache cache = cacheManager.getCache("authCache");
                    Boolean cached = cache != null ? cache.get(cacheKey, Boolean.class) : null;

                    if (cached != null) {
                        log.info("[traceId={}] 🔹 Cache hit | result={}| key={} ", traceId, cached, cacheKey);
                        return cached;
                    }

                    // 💥 真正的阻塞操作在这里执行
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

            // 3. 🌟 核心修改 3: 使用 Mono.fromCallable 和 subscribeOn
            Mono<Boolean> validationMono = Mono.fromCallable(validationCallable)
                    .subscribeOn(scheduler); // 切换到阻塞专用的 Scheduler

            return validationMono
                    .flatMap(authorized -> {
                        if (!authorized) {
                            // 失败路径：不需要额外的 MDC.set/clear，因为 HttpResponseUtils 及其后的日志应依赖 Logback 配置
                            log.warn("[traceId={}] ❌ Request blocked | IP={} | Route={} | Method={} | Path={}",
                                    traceId, ip, routeId, method, path);
                            // 返回一个带响应的 Mono
                            return HttpResponseUtils.write(exchange,
                                    HttpResponseUtils.unauthorized("Unauthorized or service unavailable"));
                        }
                        // 验证成功，继续执行过滤器链
                        return chain.filter(exchange);
                    })
                    .onErrorResume(ex -> {
                        // 错误处理路径
                        log.error("[traceId={}] ❌ Downstream unavailable | IP={} | Route={} | Method={} | Path={} | Exception={}",
                                traceId, ip, routeId, method, path, ex.toString());
                        // 返回一个带响应的 Mono
                        return HttpResponseUtils.write(exchange,
                                HttpResponseUtils.internalError("Downstream service unavailable"));
                    })
                    // 🌟 核心修改 4: 确保 Trace ID 在整个链执行完毕后被清理
                    .doFinally(signal -> TraceIdUtils.clearMdc());
        });
    }
}