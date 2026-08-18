package com.backend.gateway.config;

import com.backend.gateway.filter.AuthFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Gateway 路由配置 — 通过 Pod IP（同节点直接访问）
 */
@Slf4j
@Configuration
public class GatewayRouteConfig {

    private static final int PORT = 8080;
    private final AuthFilter authFilter;

    @Value("${svc.security:security}")
    private String securityHost;
    @Value("${svc.login:login}")
    private String loginHost;
    @Value("${svc.user:user}")
    private String userHost;
    @Value("${svc.manage:manage}")
    private String manageHost;

    public GatewayRouteConfig(AuthFilter authFilter) {
        this.authFilter = authFilter;
    }

    @Bean
    public RouteLocator customRoutes(RouteLocatorBuilder builder) {
        log.info("🔄 Gateway routes via Pod IP: security={}, login={}", securityHost, loginHost);

        return builder.routes()
            .route("security", r -> r.path("/security/**")
                .filters(f -> f.filter(authFilter.createAuthFilter()).stripPrefix(1))
                .uri("http://" + securityHost + ":" + PORT))
            .route("login", r -> r.path("/login/**")
                .filters(f -> f.filter(authFilter.createAuthFilter()).stripPrefix(1))
                .uri("http://" + loginHost + ":" + PORT))
            .route("auth", r -> r.path("/auth/**")
                .filters(f -> f.filter(authFilter.createAuthFilter()).stripPrefix(1))
                .uri("http://" + securityHost + ":" + PORT))
            .route("user", r -> r.path("/user/**")
                .filters(f -> f.filter(authFilter.createAuthFilter()).stripPrefix(1))
                .uri("http://" + userHost + ":" + PORT))
            .route("manage", r -> r.path("/manage/**")
                .filters(f -> f.filter(authFilter.createAuthFilter()).stripPrefix(1))
                .uri("http://" + manageHost + ":" + PORT))
            .build();
    }
}