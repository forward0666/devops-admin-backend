package com.backend.bot.service;

import com.backend.bot.util.LogUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 域名加白业务逻辑服务的实现。
 * 这是一个模拟实现，实际应用中会包含对外部API或系统的调用。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WhitelistServiceImpl implements WhitelistService {

    private final org.springframework.data.redis.core.ReactiveStringRedisTemplate redisTemplate;

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
}