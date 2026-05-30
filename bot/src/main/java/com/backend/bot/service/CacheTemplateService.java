package com.backend.bot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.time.Duration;

/**
 * 通用的旁路缓存模板服务 (Cache-Aside Pattern Template Service)。
 * 封装了 "查缓存 -> 查数据库 -> 写入缓存" 的重复逻辑，支持 Value 和 Set 两种模式。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CacheTemplateService {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Scheduler blockingTaskScheduler; // 注入的阻塞任务调度器

    private static final String INVALID_MARKER = "INVALID";

    /**
     * 【Value 模式】从缓存中获取对象，如果未命中，则从数据库获取并缓存。
     * 适用于缓存单个配置实体（如 BotConfigEntity）。
     *
     * @param <T> 实体类型
     * @param cacheKey Redis 键
     * @param validDuration 缓存有效期
     * @param invalidDuration 缓存穿透标记 (INVALID) 有效期
     * @param dbFetcher 数据库获取逻辑 (传入 Mono<T>)
     * @param entityClass 实体类 Class
     * @return Mono<T> 实体对象
     */
    public <T> Mono<T> getValueOrFetch(
            String cacheKey,
            Duration validDuration,
            Duration invalidDuration,
            Mono<T> dbFetcher,
            Class<T> entityClass) {

        // 1. 尝试从 Redis 获取 JSON 字符串
        return redisTemplate.opsForValue().get(cacheKey)
                .flatMap(json -> {
                    // 2. 命中 "INVALID" 标记，返回空
                    if (INVALID_MARKER.equals(json)) {
                        log.debug("💬 Cache marked as INVALID for key: {}", cacheKey);
                        return Mono.empty();
                    }

                    // 3. 命中有效数据，在阻塞线程中反序列化并返回
                    return Mono.fromCallable(() -> objectMapper.readValue(json, entityClass))
                            .subscribeOn(blockingTaskScheduler)
                            .doOnSuccess(entity -> log.debug("💬 Value cache hit for key: {}", cacheKey))
                            .onErrorResume(JsonProcessingException.class, e -> {
                                log.error("🚨 Failed to deserialize cache for key: {}. Falling back to DB.", cacheKey, e);
                                // 反序列化失败，当作缓存未命中，继续走 DB 逻辑
                                return Mono.empty();
                            });
                })
                .switchIfEmpty(
                        // 4. 缓存未命中或反序列化失败：查询数据库
                        dbFetcher
                                .flatMap(entity -> {
                                    // 5. 数据库命中：序列化并缓存，然后返回
                                    try {
                                        String entityJson = objectMapper.writeValueAsString(entity);
                                        return redisTemplate.opsForValue()
                                                .set(cacheKey, entityJson, validDuration)
                                                .thenReturn(entity)
                                                .doOnSuccess(e -> log.debug("✅ Value successfully fetched from DB and cached for key: {}", cacheKey));
                                    } catch (JsonProcessingException e) {
                                        log.error("🚨 Failed to serialize entity for key: {}. Returning DB result without caching.", cacheKey, e);
                                        return Mono.just(entity); // 序列化失败，跳过缓存，直接返回结果
                                    }
                                })
                                .switchIfEmpty(
                                        // 6. 数据库未命中：缓存 "INVALID" 标记，防止缓存穿透
                                        redisTemplate.opsForValue().set(cacheKey, INVALID_MARKER, invalidDuration)
                                                .then(Mono.empty())
                                )
                )
                // 7. 捕获并记录其他潜在错误（如 Redis 连接失败）
                .onErrorResume(e -> {
                    log.error("🚨 Error during Value cache operation for key: {}", cacheKey, e);
                    return Mono.empty();
                });
    }


    /**
     * 【Set 模式】检查元素是否在 Set 缓存中，如果不在，则通过数据库查询，并将结果缓存到 Set 中。
     * 适用于白名单、授权检查等需要快速判断元素是否属于集合的场景。
     *
     * @param cacheKey Redis 键
     * @param element 待检查的元素 (通常是 String)
     * @param validDuration 缓存有效期
     * @param dbFetcher 数据库获取逻辑 (传入 Mono<Boolean>，表示数据库中是否存在)
     * @return Mono<Boolean> - true 如果元素存在于 Set 或 DB 中，false 否则。
     */
    public Mono<Boolean> checkSetMembershipOrFetch(
            String cacheKey,
            String element,
            Duration validDuration,
            Mono<Boolean> dbFetcher) {

        // 1. 尝试从 Redis Set 中检查元素是否存在
        return redisTemplate.opsForSet().isMember(cacheKey, element)
                .flatMap(isMember -> {
                    if (Boolean.TRUE.equals(isMember)) {
                        log.debug("✅ Set cache hit: element {} is a member of key {}", element, cacheKey);
                        return Mono.just(true); // Redis 命中
                    }

                    // 2. Redis 未命中：尝试从数据库获取
                    log.debug("🔍 Set cache miss: checking DB for element {} in key {}", element, cacheKey);
                    return dbFetcher
                            .flatMap(isAuthorized -> {
                                if (Boolean.TRUE.equals(isAuthorized)) {
                                    // 3. 数据库命中：缓存到 Redis Set 并设置过期时间
                                    return redisTemplate.opsForSet().add(cacheKey, element)
                                            .flatMap(count -> redisTemplate.expire(cacheKey, validDuration))
                                            .thenReturn(true)
                                            .doOnSuccess(v -> log.debug("✅ Element {} fetched from DB and added to Set cache {}", element, cacheKey));
                                } else {
                                    // 4. 数据库未命中：不缓存，返回 false
                                    log.debug("❌ Element {} not found in DB for key {}", element, cacheKey);
                                    return Mono.just(false);
                                }
                            });
                })
                .onErrorResume(e -> {
                    log.error("🚨 Error during Set cache operation for key: {}. Defaulting to false.", cacheKey, e);
                    // 数据库或 Redis 出现故障时，默认返回 false (安全策略)
                    return Mono.just(false);
                });
    }
}