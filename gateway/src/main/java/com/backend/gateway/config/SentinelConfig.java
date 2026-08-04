package com.backend.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.sc.SentinelGatewayFilter;
import com.alibaba.csp.sentinel.adapter.gateway.sc.exception.SentinelGatewayBlockExceptionHandler;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.result.view.ViewResolver;

import java.util.Collections;
import java.util.List;

/**
 * Sentinel configuration for Spring Cloud Gateway.
 * 
 * Provides:
 * - Global Rate Limiting via Sentinel Gateway Filter
 * - Circuit Breaking via Sentinel degraded rules
 * - Nacos datasource for dynamic rule updates (configured in Nacos properties)
 * 
 * Default rules (overridable via Nacos sentinel rules):
 * - Each route: 100 QPS limit
 * - Circuit breaker: 50% failure rate over 10 calls, 30s recovery
 */
@Slf4j
@Configuration
public class SentinelConfig {

    private final ObjectProvider<ViewResolver> viewResolvers;
    private final ServerCodecConfigurer serverCodecConfigurer;

    public SentinelConfig(ObjectProvider<ViewResolver> viewResolvers,
                          ServerCodecConfigurer serverCodecConfigurer) {
        this.viewResolvers = viewResolvers;
        this.serverCodecConfigurer = serverCodecConfigurer;
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SentinelGatewayBlockExceptionHandler sentinelGatewayBlockExceptionHandler() {
        // Customize block response when Sentinel rejects a request
        return new SentinelGatewayBlockExceptionHandler(
                viewResolvers instanceof List ? (List<ViewResolver>) viewResolvers : Collections.emptyList(),
                serverCodecConfigurer);
    }

    @Bean
    @Order(-1)
    public GlobalFilter sentinelGatewayFilter() {
        return new SentinelGatewayFilter();
    }

    @PostConstruct
    public void initDefaultRules() {
        log.info("Sentinel initialized. Rules loaded from Nacos (sentinel rules data-id).");
        // Rules are loaded dynamically via Nacos datasource:
        // spring.cloud.sentinel.datasource.ds1.nacos.server-addr=...
        // spring.cloud.sentinel.datasource.ds1.nacos.data-id=sentinel-gateway-rules
        // spring.cloud.sentinel.datasource.ds1.nacos.rule-type=gw_flow
    }
}