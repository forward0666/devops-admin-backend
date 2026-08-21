package com.backend.gateway.config;

import com.backend.gateway.filter.AuthFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Gateway 路由配置 — 通过 K8s service DNS
 * 所有后端模块通过 service-name:port 访问
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
        log.info("🔄 Gateway routes via K8s service DNS");

        return builder.routes()
            .route("security", r -> r.path("/security/**")
                .filters(f -> f.filter(authFilter.createAuthFilter()).stripPrefix(1))
                .uri("http://security:8080"))
            .route("login", r -> r.path("/login/**")
                .filters(f -> f.filter(authFilter.createAuthFilter()).stripPrefix(1))
                .uri("http://login:8080"))
            .route("auth", r -> r.path("/auth/**")
                .filters(f -> f.filter(authFilter.createAuthFilter()).stripPrefix(1))
                .uri("http://security:8080"))
            .route("admin", r -> r.path("/admin/**")
                .filters(f -> f.filter(authFilter.createAuthFilter()))
                .uri("http://manage:8080"))
            .route("user", r -> r.path("/user/**")
                .filters(f -> f.filter(authFilter.createAuthFilter()).stripPrefix(1))
                .uri("http://user:8080"))
            .route("manage", r -> r.path("/manage/**")
                .filters(f -> f.filter(authFilter.createAuthFilter()).stripPrefix(1))
                .uri("http://manage:8080"))
            .route("monitor", r -> r.path("/monitor/**")
                .filters(f -> f.filter(authFilter.createAuthFilter()).stripPrefix(1))
                .uri("http://monitor:8080"))
            .route("bot", r -> r.path("/bot/**")
                .filters(f -> f.filter(authFilter.createAuthFilter()).stripPrefix(1))
                .uri("http://bot:8080"))
            .route("agent", r -> r.path("/agent/**")
                .filters(f -> f.filter(authFilter.createAuthFilter()).stripPrefix(1))
                .uri("http://agent:8080"))
            .route("domain", r -> r.path("/domain/**")
                .filters(f -> f.filter(authFilter.createAuthFilter()).stripPrefix(1))
                .uri("http://domain:8080"))
            .build();
    }
}