package com.backend.gateway.filter;

import com.backend.gateway.config.BaseAuthConfig;
import lombok.extern.slf4j.Slf4j;
import network.ClientIpUtils;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.server.reactive.ServerHttpResponse;
import security.AuthValidationUtils;
import webflux.ExchangeUtils;

/**
 * 抽象认证过滤器
 * 子类只需实现 `getSecret()` 和 `shouldBlockRequest()` 方法即可
 */
@Slf4j
public abstract class AbstractAuthFilter<T extends BaseAuthConfig> extends AbstractGatewayFilterFactory<T> {

    public AbstractAuthFilter(Class<T> configClass) {
        super(configClass);
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

            // 验证加密头
            boolean authorized = AuthValidationUtils.isAuthorized(exchange, getSecret());
            if (!authorized) {
                AuthValidationUtils.unauthorizedResponse(response, "[" + this.getClass().getSimpleName() + "]", ip, method, path);
                return response.setComplete();
            }

            // 子类自定义拦截逻辑
            if (authorizedRequest(method)) {
                AuthValidationUtils.methodNotAllowedResponse(response, "[" + this.getClass().getSimpleName() + "]", ip, method, path);
                return response.setComplete();
            }

            // 成功：标记响应并放行
            AuthValidationUtils.authorizedResponse(response);
            log.info("[{}] ✅ Authorized: IP={}, Route={}, Method={}, Path={}",
                    this.getClass().getSimpleName(), ip, routeId, method, path);
            return chain.filter(exchange);
        };
    }
}
