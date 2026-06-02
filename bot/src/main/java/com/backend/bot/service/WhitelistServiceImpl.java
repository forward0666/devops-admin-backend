package com.backend.bot.service;

import com.backend.bot.util.LogUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 域名加白业务逻辑服务的实现。
 * 这是一个模拟实现，实际应用中会包含对外部API或系统的调用。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WhitelistServiceImpl implements WhitelistService {

    private final org.springframework.data.redis.core.ReactiveStringRedisTemplate redisTemplate;
    private final WebClient.Builder webClientBuilder;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Value("${bot.cloudflare-service-url:}")
    private String cloudflareServiceUrl;

    @Value("${bot.cloudflare-service-name:cloudflare}")
    private String cloudflareServiceName;

    private String getCloudflareBaseUrl() {
        return (cloudflareServiceUrl != null && !cloudflareServiceUrl.isBlank())
                ? cloudflareServiceUrl : "lb://" + cloudflareServiceName;
    }

    private String getRedisKey(String domainType) {
        return "bot:whitelist:" + (domainType != null ? domainType : "default");
    }

    @Override
    public Mono<Boolean> addIpToWhitelist(String ip, String username, String domainType) {
        return Mono.deferContextual(contextView -> {
            final String traceLogPrefix = LogUtils.prepareMdcAndGetPrefix(contextView);
            log.info("{} 🌐 Whitelist: add IP {} to {} for user {}", traceLogPrefix, ip, domainType, username);
            return redisTemplate.opsForSet().add(getRedisKey(domainType), ip)
                    .map(count -> count > 0)
                    .doOnNext(success -> {
                        if (Boolean.TRUE.equals(success))
                            log.info("{} ✅ Whitelist: added IP {}", traceLogPrefix, ip);
                    })
                    .onErrorResume(e -> {
                        log.error("{} ❌ Whitelist: failed to add IP {}", traceLogPrefix, ip, e);
                        return Mono.just(false);
                    });
        });
    }

    @Override
    public Mono<Boolean> removeIpFromWhitelist(String ip, String domainType) {
        return redisTemplate.opsForSet().remove(getRedisKey(domainType), ip)
                .map(count -> count > 0)
                .onErrorResume(e -> {
                    log.error("❌ Whitelist: failed to remove IP {}", ip, e);
                    return Mono.just(false);
                });
    }

    @Override
    public Mono<java.util.List<String>> getWhitelistIps(String domainType) {
        return redisTemplate.opsForSet().members(getRedisKey(domainType))
                .map(Object::toString)
                .collectList()
                .onErrorResume(e -> {
                    log.error("❌ Whitelist: failed to get IPs", e);
                    return Mono.just(java.util.List.of());
                });
    }

    @Override
    public Mono<String> addCfWhitelistIp(Long projectId, String ruleId, String ip, String username, String env, String operator) {
        WebClient webClient = webClientBuilder.baseUrl(getCloudflareBaseUrl()).build();
        Map<String, Object> body = Map.of(
                "projectId", projectId,
                "ruleId", ruleId,
                "ip", ip,
                "username", username != null ? username : "",
                "env", env != null ? env : "",
                "operator", operator != null ? operator : ""
        );
        return webClient.post().uri("/whitelist")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(resp -> String.valueOf(resp.getOrDefault("message", "操作完成")))
                .onErrorResume(e -> {
                    String errMsg = e.getMessage();
                    if (e instanceof org.springframework.web.reactive.function.client.WebClientResponseException wce) {
                        try {
                            Map<String, Object> errBody = objectMapper.readValue(wce.getResponseBodyAsString(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
                            Object detail = errBody.get("detail");
                            if (detail != null) errMsg = String.valueOf(detail);
                        } catch (Exception ex) { log.debug("Ignored exception: {}", ex.getMessage()); }
                    }
                    log.error("❌ CF Whitelist: failed to add IP {}: {}", ip, errMsg);
                    return Mono.just("失败: " + errMsg);
                });
    }
}