package com.backend.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Gateway 路由配置 — 全部迁移到 Nacos 配置中心管理
 * 通过 spring.cloud.gateway.routes 在 Nacos 中定义
 */
@Slf4j
@Configuration
public class GatewayRouteConfig {

    @Bean
    public RouteLocator customRoutes(RouteLocatorBuilder builder) {
        log.info("🔄 Gateway routes loaded from Nacos configuration");
        // 路由现在由 Nacos gateway.properties 中的 spring.cloud.gateway.routes 管理
        // 这个 Bean 保留一个空路由以避免启动报错
        return builder.routes().build();
    }
}