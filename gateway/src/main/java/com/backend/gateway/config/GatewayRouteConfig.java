package com.backend.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class GatewayRouteConfig {

    @Bean
    public RouteLocator customRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
            // Security 服务
            .route("security", r -> r
                .path("/security/**")
                .filters(f -> f.stripPrefix(1))
                .uri("http://security:8080"))
            // Login 服务
            .route("login", r -> r
                .path("/login/**")
                .filters(f -> f.stripPrefix(1))
                .uri("http://login:8080"))
            // User 服务
            .route("user", r -> r
                .path("/user/**")
                .filters(f -> f.stripPrefix(1))
                .uri("http://user:8080"))
            // Manage 服务
            .route("manage", r -> r
                .path("/manage/**")
                .filters(f -> f.stripPrefix(1))
                .uri("http://manage:8080"))
            // Monitor 服务
            .route("monitor", r -> r
                .path("/monitor/**")
                .filters(f -> f.stripPrefix(1))
                .uri("http://monitor:8080"))
            // Bot 服务
            .route("bot", r -> r
                .path("/bot/**")
                .filters(f -> f.stripPrefix(1))
                .uri("http://bot:8080"))
            // Agent 服务
            .route("agent", r -> r
                .path("/agent/**")
                .filters(f -> f.stripPrefix(1))
                .uri("http://agent:8080"))
            // Domain 服务
            .route("domain", r -> r
                .path("/domain/**")
                .filters(f -> f.stripPrefix(1))
                .uri("http://domain:8080"))
            .build();
    }
}