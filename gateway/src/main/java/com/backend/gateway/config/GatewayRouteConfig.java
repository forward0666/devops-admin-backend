package com.backend.gateway.config;

import com.backend.gateway.filter.AuthFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Gateway 路由配置 — 通过 service DNS (K8s ClusterIP) 实现服务发现
 * 不再使用 Pod IP 或 NodePort
 */
@Slf4j
@Configuration
public class GatewayRouteConfig {

    private static final int PORT = 8080;
    private final AuthFilter authFilter;

    public GatewayRouteConfig(AuthFilter authFilter) {
        this.authFilter = authFilter;
    }

    @Bean
    public RouteLocator customRoutes(RouteLocatorBuilder builder) {
        log.info("🔄 Gateway routes via service DNS (ClusterIP)");

        return builder.routes()
            .route("security", r -> r.path("/security/**")
                .filters(f -> f.filter(authFilter.createAuthFilter()).stripPrefix(1))
                .uri("http://security:" + PORT))
            .route("login", r -> r.path("/login/**")
                .filters(f -> f.filter(authFilter.createAuthFilter()).stripPrefix(1))
                .uri("http://login:" + PORT))
            .route("auth", r -> r.path("/auth/**")
                .filters(f -> f.filter(authFilter.createAuthFilter()).stripPrefix(1))
                .uri("http://security:" + PORT))
            .route("user", r -> r.path("/user/**")
                .filters(f -> f.filter(authFilter.createAuthFilter()).stripPrefix(1))
                .uri("http://user:" + PORT))
            .route("admin", r -> r.path("/admin/**")
                .filters(f -> f.filter(authFilter.createAuthFilter()))
                .uri("http://security:8080"))
            .route("manage", r -> r.path("/manage/**")
                .filters(f -> f.filter(authFilter.createAuthFilter()).stripPrefix(1))
                .uri("http://manage:" + PORT))
            .route("monitor", r -> r.path("/monitor/**")
                .filters(f -> f.filter(authFilter.createAuthFilter()).stripPrefix(1))
                .uri("http://monitor:" + PORT))
            .route("bot", r -> r.path("/bot/**")
                .filters(f -> f.filter(authFilter.createAuthFilter()).stripPrefix(1))
                .uri("http://bot:" + PORT))
            .route("agent", r -> r.path("/agent/**")
                .filters(f -> f.filter(authFilter.createAuthFilter()).stripPrefix(1))
                .uri("http://agent:" + PORT))
            .route("domain", r -> r.path("/domain/**")
                .filters(f -> f.filter(authFilter.createAuthFilter()).stripPrefix(1))
                .uri("http://domain:" + PORT))
            .build();
    }
}