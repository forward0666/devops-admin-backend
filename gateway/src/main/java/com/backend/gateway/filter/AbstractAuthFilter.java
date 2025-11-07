package com.backend.gateway.filter;

import com.backend.gateway.config.BaseAuthConfig;
import lombok.extern.slf4j.Slf4j;
import network.ClientIpUtils;
import network.HttpResponseUtils;
import org.slf4j.MDC;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import reactor.core.publisher.Mono;
import security.AuthValidationUtils;
import webflux.ExchangeUtils;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * 抽象认证过滤器（带线程池异步支持 & traceId & 缓存 & 下游不可用返回统一 JSON）
 */
@Slf4j
public abstract class AbstractAuthFilter<T extends BaseAuthConfig> extends AbstractGatewayFilterFactory<T> {

    private final ExecutorService executorService;
    private final CacheManager cacheManager; // 注入 CaffeineCacheManager

    public AbstractAuthFilter(Class<T> configClass, ExecutorService executorService, CacheManager cacheManager) {
        super(configClass);
        this.executorService = executorService;
        this.cacheManager = cacheManager;
    }

    /** 获取当前 Filter 的密钥 */
    protected abstract String getSecret();

    /** 是否拒绝该请求（子类可实现特定策略，如 BigData 拒绝 GET） */
    protected boolean authorizedRequest(String method) {
        return false;
    }

    @Override
    public GatewayFilter apply(T config) {
        return (exchange, chain) -> {
            if (!config.isEnabled()) {
                return chain.filter(exchange);
            }

            String method = ExchangeUtils.getMethod(exchange);
            String path = ExchangeUtils.getPath(exchange);
            String routeId = ExchangeUtils.getRouteId(exchange);
            String ip = ClientIpUtils.getClientIp(exchange);
            ServerHttpResponse response = ExchangeUtils.getResponse(exchange);

            // 生成全链路 traceId
            String traceId = UUID.randomUUID().toString();

            // 异步验证
            CompletableFuture<Boolean> validationFuture = CompletableFuture.supplyAsync(() -> {
                MDC.put("traceId", traceId);
                try {
                    String cacheKey = "auth:" + ip + ":" + method + ":" +path;
                    Cache cache = cacheManager.getCache("authCache");
                    Boolean cached = cache != null ? cache.get(cacheKey, Boolean.class) : null;

                    if (cached != null) {
                        log.info("[{}] 🔹 Cache hit | traceId={} | key={} | result={}",
                                this.getClass().getSimpleName(), traceId, cacheKey, cached);
                        return cached;
                    }

                    boolean authorized = AuthValidationUtils.isAuthorized(exchange, getSecret());
                    if (!authorized) {
                        log.warn("[{}] ❌ Unauthorized request | traceId={} | IP={} | Route={} | Method={} | Path={}",
                                this.getClass().getSimpleName(), traceId, ip, routeId, method, path);
                        if (cache != null) cache.put(cacheKey, false);
                        return false;
                    } else if (authorizedRequest(method)) {
                        log.warn("[{}] ❌ Method not allowed | traceId={} | IP={} | Route={} | Method={} | Path={}",
                                this.getClass().getSimpleName(), traceId, ip, routeId, method, path);
                        if (cache != null) cache.put(cacheKey, false);
                        return false;
                    } else {
                        log.info("[{}] ✅ Authorized request | traceId={} | IP={} | Route={} | Method={} | Path={}",
                                this.getClass().getSimpleName(), traceId, ip, routeId, method, path);
                        if (cache != null) cache.put(cacheKey, true);
                        log.info("[{}] 🔹 Cache put | traceId={} | key={} | value=true",
                                this.getClass().getSimpleName(), traceId, cacheKey);
                        return true;
                    }
                } finally {
                    MDC.remove("traceId");
                }
            }, executorService);

            return Mono.fromFuture(validationFuture)
                    .flatMap(authorized -> {
                        if (!authorized) {
                            return writeJsonResponse(response, HttpResponseUtils.internalError("Unauthorized or service unavailable"));
                        }
                        return chain.filter(exchange)
                                .onErrorResume(ex -> {
                                    log.error("[{}] ❌ Downstream unavailable | traceId={} | IP={} | Route={} | Method={} | Path={} | Exception={}",
                                            this.getClass().getSimpleName(), traceId, ip, routeId, method, path, ex.toString());
                                    return writeJsonResponse(response, HttpResponseUtils.internalError("Downstream service unavailable"));
                                });
                    });
        };
    }

    /** 写入统一 JSON 响应 */
    private Mono<Void> writeJsonResponse(ServerHttpResponse response, org.springframework.http.ResponseEntity<?> entity) {
        response.setStatusCode(entity.getStatusCode());
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String json = entity.getBody().toString();
        DataBuffer buffer = response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
