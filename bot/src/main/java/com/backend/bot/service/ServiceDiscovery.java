package com.backend.bot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 通过 Nacos REST API 解析服务名 → 实际地址
 * 用于 bot.user-service-name=user / bot.cf-service-name=cloudflare 等配置
 */
@Service
@Slf4j
public class ServiceDiscovery {

    private final WebClient.Builder webClientBuilder;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${spring.cloud.nacos.server-addr:192.168.86.9:8848}")
    private String nacosAddr;

    @Value("${spring.cloud.nacos.config.namespace:6c5b1db3-a808-4543-a87e-6642e372cb4f}")
    private String namespace;

    @Value("${spring.cloud.nacos.config.username:nacos}")
    private String username;

    @Value("${spring.cloud.nacos.config.password:nacos}")
    private String password;

    private static final Duration CACHE_TTL = Duration.ofSeconds(60);

    public ServiceDiscovery(WebClient.Builder webClientBuilder, ReactiveStringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.webClientBuilder = webClientBuilder;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 解析服务名，返回 http://{ip}:{port}
     * 优先 @Value 直接配的 URL，没配就走 Nacos 服务发现
     */
    public Mono<String> resolve(String serviceName) {
        String cacheKey = "nacos:svc:" + serviceName;
        return redisTemplate.opsForValue().get(cacheKey)
                .doOnNext(url -> log.debug("ServiceDiscovery cache hit: {} -> {}", serviceName, url))
                .switchIfEmpty(fetchFromNacos(serviceName)
                        .flatMap(url -> redisTemplate.opsForValue().set(cacheKey, url, CACHE_TTL).thenReturn(url)));
    }

    @SuppressWarnings("unchecked")
    private Mono<String> fetchFromNacos(String serviceName) {
        String url = String.format("http://%s/nacos/v1/ns/instance/list?serviceName=%s&namespaceId=%s&username=%s&password=%s",
                nacosAddr, serviceName, namespace, username, password);
        log.info("ServiceDiscovery: resolving '{}' from Nacos at {}", serviceName, nacosAddr);

        return webClientBuilder.build().get().uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(body -> {
                    try {
                        Map<String, Object> map = objectMapper.readValue(body, Map.class);
                        List<Map<String, Object>> hosts = (List<Map<String, Object>>) map.get("hosts");
                        if (hosts == null || hosts.isEmpty()) {
                            log.warn("ServiceDiscovery: no healthy instances for '{}'", serviceName);
                            return Mono.empty();
                        }
                        Map<String, Object> instance = hosts.stream()
                                .filter(h -> Boolean.TRUE.equals(h.get("healthy")))
                                .findFirst()
                                .orElse(hosts.get(0));
                        String ip = String.valueOf(instance.get("ip"));
                        int port = ((Number) instance.get("port")).intValue();
                        String serviceUrl = String.format("http://%s:%d", ip, port);
                        log.info("ServiceDiscovery: {} -> {}", serviceName, serviceUrl);
                        return Mono.just(serviceUrl);
                    } catch (Exception e) {
                        log.error("ServiceDiscovery: parse error for '{}': {}", serviceName, e.getMessage());
                        return Mono.empty();
                    }
                })
                .onErrorResume(e -> {
                    log.error("ServiceDiscovery: failed to resolve '{}': {}", serviceName, e.getMessage());
                    return Mono.empty();
                });
    }
}
