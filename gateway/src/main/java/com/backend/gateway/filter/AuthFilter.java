package com.backend.gateway.filter;

import com.backend.gateway.config.BaseAuthConfig;
import lombok.extern.slf4j.Slf4j;
import network.TraceIdUtils;
import org.springframework.cache.CacheManager;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

@Slf4j
public abstract class AuthFilter<T extends BaseAuthConfig> extends AbstractGatewayFilterFactory<T> {

    private final Scheduler scheduler;
    private final CacheManager cacheManager;

    public AuthFilter(Class<T> configClass, Scheduler scheduler, CacheManager cacheManager) {
        super(configClass);
        this.scheduler = scheduler;
        this.cacheManager = cacheManager;
    }

    protected abstract boolean isWhitelistedPath(String path);

    @Override
    public GatewayFilter apply(T config) {
        if (!config.isEnabled()) {
            return (exchange, chain) -> chain.filter(exchange);
        }
        return createAuthGatewayFilter(config);
    }

    private GatewayFilter createAuthGatewayFilter(T config) {
        return (exchange, chain) -> Mono.deferContextual(contextView -> {
            String traceId = contextView.getOrEmpty("traceId")
                    .map(Object::toString).orElse("NO_TRACE_ID");

            if (!"NO_TRACE_ID".equals(traceId)) {
                TraceIdUtils.setTraceId(traceId);
            }

            String path = exchange.getRequest().getURI().getPath();

            if (isWhitelistedPath(path)) {
                log.info("[traceId={}] ✅ Whitelisted path | Path={}", traceId, path);
                return chain.filter(exchange);
            }

            // Gateway 只转发，验证由 security 模块处理
            // 移除 X-Encrypted-Data 验证逻辑
            return chain.filter(exchange);
        });
    }
}