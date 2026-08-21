package com.backend.gateway.config;

import com.backend.gateway.filter.AuthFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Gateway 路由配置 — 通过 Nacos 服务发现
 * lb://service-name 表示从 Nacos 负载均衡发现
 */
@Slf4j
@Configuration
public class GatewayRouteConfig {

    private final AuthFilter authFilter;

    public GatewayRouteConfig(AuthFilter authFilter) {
        this.authFilter = authFilter;
    }

    @Bean
    public RouteLocator customRoutes(RouteLocatorBuilder builder) {
        log.info("🔄 Gateway routes via Nacos service discovery (lb://)");

        return builder.routes()
            .route("security", r -> r.path("/security/**")
                .filters(f -> f.filter(authFilter.createAuthFilter()).stripPrefix(1))
                .uri("lb://security"))
            .route("login", r -> r.path("/login/**")
                .filters(f -> f.filter(authFilter.createAuthFilter()).stripPrefix(1))
                .uri("lb://login"))
            .route("auth", r -> r.path("/auth/**")
                .filters(f -> f.filter(authFilter.createAuthFilter()).stripPrefix(1))
                .uri("lb://security"))
            .route("admin", r -> r.path("/admin/**")
                .filters(f -> f.filter(authFilter.createAuthFilter()))
                .uri("lb://manage"))
            .route("user", r -> r.path("/user/**")
                .filters(f -> f.filter(authFilter.createAuthFilter()).stripPrefix(1))
                .uri("lb://user"))
            .route("manage", r -> r.path("/manage/**")
                .filters(f -> f.filter(authFilter.createAuthFilter()).stripPrefix(1))
                .uri("lb://manage"))
            .route("monitor", r -> r.path("/monitor/**")
                .filters(f -> f.filter(authFilter.createAuthFilter()).stripPrefix(1))
                .uri("lb://monitor"))
            .route("bot", r -> r.path("/bot/**")
                .filters(f -> f.filter(authFilter.createAuthFilter()).stripPrefix(1))
                .uri("lb://bot"))
            .route("agent", r -> r.path("/agent/**")
                .filters(f -> f.filter(authFilter.createAuthFilter()).stripPrefix(1))
                .uri("lb://agent"))
            .route("domain", r -> r.path("/domain/**")
                .filters(f -> f.filter(authFilter.createAuthFilter()).stripPrefix(1))
                .uri("lb://domain"))
            .build();
    }
}