package com.backend.gateway.config;

import com.backend.gateway.filter.AuthFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Gateway 路由配置 — 通过 NodePort 直连各服务
 * 解决 kube-router ClusterIP DNS 不通问题
 */
@Slf4j
@Configuration
public class GatewayRouteConfig {

    private static final int PORT = 8080;
    private final AuthFilter authFilter;

    @Value("${svc.node.host:192.168.86.14}")
    private String nodeHost;

    public GatewayRouteConfig(AuthFilter authFilter) {
        this.authFilter = authFilter;
    }

    @Bean
    public RouteLocator customRoutes(RouteLocatorBuilder builder) {
        log.info("🔄 Gateway routes via NodePort: host={}", nodeHost);

        return builder.routes()
            .route("security", r -> r.path("/security/**")
                .filters(f -> f.filter(authFilter.createAuthFilter()).stripPrefix(1))
                .uri("http://" + nodeHost + ":32102"))
            .route("login", r -> r.path("/login/**")
                .filters(f -> f.filter(authFilter.createAuthFilter()).stripPrefix(1))
                .uri("http://" + nodeHost + ":32105"))
            .route("auth", r -> r.path("/auth/**")
                .filters(f -> f.filter(authFilter.createAuthFilter()).stripPrefix(1))
                .uri("http://" + nodeHost + ":32102"))
            .route("user", r -> r.path("/user/**")
                .filters(f -> f.filter(authFilter.createAuthFilter()).stripPrefix(1))
                .uri("http://" + nodeHost + ":32103"))
            .route("manage", r -> r.path("/manage/**")
                .filters(f -> f.filter(authFilter.createAuthFilter()).stripPrefix(1))
                .uri("http://" + nodeHost + ":32104"))
            .route("monitor", r -> r.path("/monitor/**")
                .filters(f -> f.filter(authFilter.createAuthFilter()).stripPrefix(1))
                .uri("http://" + nodeHost + ":32106"))
            .route("bot", r -> r.path("/bot/**")
                .filters(f -> f.filter(authFilter.createAuthFilter()).stripPrefix(1))
                .uri("http://" + nodeHost + ":32107"))
            .route("agent", r -> r.path("/agent/**")
                .filters(f -> f.filter(authFilter.createAuthFilter()).stripPrefix(1))
                .uri("http://" + nodeHost + ":32108"))
            .route("domain", r -> r.path("/domain/**")
                .filters(f -> f.filter(authFilter.createAuthFilter()).stripPrefix(1))
                .uri("http://" + nodeHost + ":32109"))
            .build();
    }
}