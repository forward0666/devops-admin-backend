package com.backend.bot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
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
 * 通过 Nacos REST API 动态获取服务地址
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ServiceDiscovery {

    private final WebClient.Builder webClientBuilder;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${spring.cloud.nacos.server-addr:${NACOS_HOST:192.168.86.9}:${NACOS_PORT:8848}}")
    private String nacosAddr;

    @Value("${spring.cloud.nacos.config.namespace:${NACOS_NAMESPACE:6c5b1db3-a808-4543-a87e-6642e372cb4f}}")
    private String namespace;

    @Value("${spring.cloud.nacos.username:${NACOS_USERNAME:nacos}}")
    private String username;

    @Value("${spring.cloud.nacos.password:${NACOS_PASSWORD:nacos}}")
    private String password;

    private static final Duration CACHE_TTL = Duration.ofSeconds(60);

    /**
     * 获取服务实例地址，格式: http://{ip}:{port}
     * 优先从 Redis 缓存读取，缓存未命中则查 Nacos
     */
    public Mono<String> getServiceUrl(String serviceName) {
        String cacheKey = "nacos:service:" + serviceName;
        return redisTemplate.opsForValue().get(cacheKey)
                .doOnNext(url -> log.debug("NacosServiceDiscovery cache hit: {} -> {}", serviceName, url))
                .switchIfEmpty(fetchFromNacos(serviceName)
                        .flatMap(url -> redisTemplate.opsForValue().set(cacheKey, url, CACHE_TTL).thenReturn(url)));
    }

    @SuppressWarnings("unchecked")
    private Mono<String> fetchFromNacos(String serviceName) {
        String url = String.format("http://%s/nacos/v1/ns/instance/list?serviceName=%s&namespaceId=%s&username=%s&password=%s",
                nacosAddr, serviceName, namespace, username, password);
        log.info("NacosServiceDiscovery: fetching {} from Nacos at {}", serviceName, nacosAddr);

        return webClientBuilder.build().get().uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(body -> {
                    try {
                        Map<String, Object> map = objectMapper.readValue(body, Map.class);
                        List<Map<String, Object>> hosts = (List<Map<String, Object>>) map.get("hosts");
                        if (hosts == null || hosts.isEmpty()) {
                            log.warn("NacosServiceDiscovery: no instances for {}", serviceName);
                            return Mono.empty();
                        }
                        // 取第一个 healthy 实例
                        Map<String, Object> instance = hosts.stream()
                                .filter(h -> Boolean.TRUE.equals(h.get("healthy")))
                                .findFirst()
                                .orElse(hosts.get(0));
                        String ip = String.valueOf(instance.get("ip"));
                        int port = ((Number) instance.get("port")).intValue();
                        String serviceUrl = String.format("http://%s:%d", ip, port);
                        log.info("NacosServiceDiscovery: {} -> {}", serviceName, serviceUrl);
                        return Mono.just(serviceUrl);
                    } catch (Exception e) {
                        log.error("NacosServiceDiscovery: failed to parse response for {}: {}", serviceName, e.getMessage());
                        return Mono.empty();
                    }
                })
                .onErrorResume(e -> {
                    log.error("NacosServiceDiscovery: failed to fetch {}: {}", serviceName, e.getMessage());
                    return Mono.empty();
                });
    }
}
