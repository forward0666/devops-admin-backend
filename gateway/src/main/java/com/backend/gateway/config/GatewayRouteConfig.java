package com.backend.gateway.config;

import com.backend.gateway.filter.AuthFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class GatewayRouteConfig {

    @Value("${svc.security:security}")
    private String securityHost;

    @Value("${svc.login:login}")
    private String loginHost;

    @Value("${svc.user:user}")
    private String userHost;

    @Value("${svc.manage:manage}")
    private String manageHost;

    @Value("${svc.monitor:monitor}")
    private String monitorHost;

    @Value("${svc.bot:bot}")
    private String botHost;

    @Value("${svc.agent:agent}")
    private String agentHost;

    @Value("${svc.domain:domain}")
    private String domainHost;

    private static final int PORT = 8080;

    private final AuthFilter authFilter;

    public GatewayRouteConfig(AuthFilter authFilter) {
        this.authFilter = authFilter;
    }

    @Bean
    public RouteLocator customRoutes(RouteLocatorBuilder builder) {
        log.info("🔄 Gateway routes initializing with hosts: security={}, login={}, user={}",
            securityHost, loginHost, userHost);

        return builder.routes()
            .route("security", r -> r
                .path("/security/**")
                .filters(f -> f
                    .filter(authFilter.createAuthFilter())
                    .stripPrefix(1))
                .uri("http://" + securityHost + ":" + PORT))
            .route("login", r -> r
                .path("/login/**")
                .filters(f -> f
                    .filter(authFilter.createAuthFilter())
                    .stripPrefix(1))
                .uri("http://" + loginHost + ":" + PORT))
            .route("auth", r -> r
                .path("/auth/**")
                .filters(f -> f
                    .filter(authFilter.createAuthFilter())
                    .stripPrefix(1))
                .uri("http://" + securityHost + ":" + PORT))
            .route("user", r -> r
                .path("/user/**")
                .filters(f -> f
                    .filter(authFilter.createAuthFilter())
                    .stripPrefix(1))
                .uri("http://" + userHost + ":" + PORT))
            .route("manage", r -> r
                .path("/manage/**")
                .filters(f -> f
                    .filter(authFilter.createAuthFilter())
                    .stripPrefix(1))
                .uri("http://" + manageHost + ":" + PORT))
            .route("monitor", r -> r
                .path("/monitor/**")
                .filters(f -> f
                    .filter(authFilter.createAuthFilter())
                    .stripPrefix(1))
                .uri("http://" + monitorHost + ":" + PORT))
            .route("bot", r -> r
                .path("/bot/**")
                .filters(f -> f
                    .filter(authFilter.createAuthFilter())
                    .stripPrefix(1))
                .uri("http://" + botHost + ":" + PORT))
            .route("agent", r -> r
                .path("/agent/**")
                .filters(f -> f
                    .filter(authFilter.createAuthFilter())
                    .stripPrefix(1))
                .uri("http://" + agentHost + ":" + PORT))
            .route("domain", r -> r
                .path("/domain/**")
                .filters(f -> f
                    .filter(authFilter.createAuthFilter())
                    .stripPrefix(1))
                .uri("http://" + domainHost + ":" + PORT))
            .build();
    }
}