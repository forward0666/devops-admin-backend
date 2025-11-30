package com.backend.bot.service;

import com.backend.bot.dto.BotRegisterDto;
import com.backend.bot.entity.BotConfigEntity;
import com.backend.bot.repository.BotRepository; // 假设 BotRepository 存在
import com.backend.bot.vo.BotVo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers; // 引入 Schedulers
import com.backend.bot.repository.BotAuthorizedChatRepository; // <--- 新增导入
import java.time.Duration;

@Service
@Slf4j
@RequiredArgsConstructor
public class BotCoreService {

    private final BotRepository botRepository;
    private final BotClientService botClient;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final BotAuthorizedChatRepository authorizedChatRepository; // <--- 确保注入

    private static final Duration CACHE_VALID_DURATION = Duration.ofHours(1);
    private static final Duration CACHE_INVALID_DURATION = Duration.ofMinutes(5);
    private static final String CACHE_ENTITY_PREFIX = "bot:entity:name:";
    private static final String CACHE_WHITELIST_PREFIX = "bot:whitelist:set:";


    public Mono<BotVo> registerNewBot(BotRegisterDto dto) {

        BotConfigEntity config = new BotConfigEntity();
        config.setBotUsername(dto.getBotUsername());
        config.setBotToken(dto.getToken());
        config.setBotName(dto.getBotName());
        config.setStatus(1);

        return botRepository.save(config)
                .flatMap(savedConfig -> {
                    // 2. 注册 Webhook
                    return botClient.setWebhook(savedConfig.getBotToken(), savedConfig.getBotName(), dto.getSecretToken())
                            .thenReturn(savedConfig);
                })
                .flatMap(this::cacheBotEntity) // 3. 响应式缓存
                .map(this::convertToVo);
    }

    public Mono<BotConfigEntity> findByBotName(String botName) {
        String entityCacheKey = CACHE_ENTITY_PREFIX + botName;

        // 1. 尝试从 Redis 缓存中读取 Entity JSON
        return redisTemplate.opsForValue().get(entityCacheKey)
                .flatMap(json -> {
                    if ("INVALID".equals(json)) {
                        log.debug("💬 Bot name cache marked as INVALID: {}", botName);
                        return Mono.empty();
                    }

                    // 🟢 关键修复：将阻塞的 objectMapper.readValue 移动到独立的线程池
                    return Mono.fromCallable(() -> {
                                try {
                                    BotConfigEntity entity = objectMapper.readValue(json, BotConfigEntity.class);
                                    log.debug("💬 Bot entity cache hit for name: {}", botName);
                                    return entity;
                                } catch (JsonProcessingException e) {
                                    log.error("🚨 Failed to deserialize BotEntity from Redis for name: {}, attempting DB lookup", botName, e);
                                    throw new RuntimeException(e);
                                }
                            })
                            .subscribeOn(Schedulers.boundedElastic()); // 切换到阻塞线程池
                })
                .onErrorResume(RuntimeException.class, e -> {
                    return Mono.empty();
                })
                // 2. 缓存未命中：查询 R2DBC 数据库
                .switchIfEmpty(
                        botRepository.findByBotName(botName)
                                .flatMap(this::cacheBotEntity) // 3. 缓存结果
                                .switchIfEmpty(
                                        // 4. 数据库中不存在：缓存 INVALID 标记
                                        redisTemplate.opsForValue().set(entityCacheKey, "INVALID", CACHE_INVALID_DURATION)
                                                .then(Mono.empty())
                                )
                );
    }

    /**
     * 辅助方法：将 BotEntity 响应式缓存到 Redis (只缓存 Bot Name 键)
     */
    private Mono<BotConfigEntity> cacheBotEntity(BotConfigEntity entity) {
        if (entity == null) return Mono.empty();

        String entityKey = CACHE_ENTITY_PREFIX + entity.getBotName();

        try {
            String entityJson = objectMapper.writeValueAsString(entity);

            // 缓存 Entity 详情
            Mono<Boolean> cacheEntity = redisTemplate.opsForValue()
                    .set(entityKey, entityJson, CACHE_VALID_DURATION);

            return cacheEntity
                    .thenReturn(entity)
                    .doOnSuccess(e -> log.debug("💬 Successfully cached bot entity by name: {}", entity.getBotName()))
                    .doOnError(e -> log.error("🚨 Failed to cache bot entity by name", e));

        } catch (JsonProcessingException e) {
            log.error("🚨 Failed to serialize BotEntity for caching", e);
            return Mono.just(entity);
        }
    }

    private BotVo convertToVo(BotConfigEntity botEntity) {
        // 占位符，实现 Entity 到 VO 的转换逻辑
        BotVo vo = new BotVo();
        if (botEntity != null) {
            // conversion logic
        }
        return vo;
    }

    /**
     * 异步检查指定的 Chat ID 是否在 Bot 的白名单中。
     * 流程：Redis Set Check -> MySQL Query -> Redis Set Cache
     *
     * @param botConfigId 机器人配置ID (对应 bot_config.id)
     * @param chatId      要验证的聊天ID
     * @return Mono<Boolean> - true 如果允许，false 如果不允许。
     */

    public Mono<Boolean> isChatIdAuthorized(Long botConfigId, Long chatId) {
        if (botConfigId == null || chatId == null) {
            return Mono.just(false);
        }
    // Redis Key: bot:whitelist:set:{botConfigId}
        String redisKey = CACHE_WHITELIST_PREFIX + botConfigId;
        String chatIdStr = String.valueOf(chatId);

        // 1. 尝试从 Redis Set 中检查是否存在
        return redisTemplate.opsForSet().isMember(redisKey, chatIdStr)
                .flatMap(isMember -> {
                    if (Boolean.TRUE.equals(isMember)) {
                        log.debug("✅ Chat ID {} cache hit in Redis whitelist for bot {}", chatId, botConfigId);
                        return Mono.just(true); // Redis 命中，授权成功
                    }

                    // 2. Redis 未命中：查询 MySQL
                    log.debug("🔍 Chat ID {} not found in Redis. Checking MySQL for bot {}", chatId, botConfigId);
                    return authorizedChatRepository.findByBotConfigIdAndChatId(botConfigId, chatId)
                            .flatMap(entity -> {
                                log.info("✅ Chat ID {} found in MySQL whitelist. Caching to Redis for bot {}.", chatId, botConfigId);

                                // 3. MySQL 命中：缓存到 Redis Set
                                // OpsForSet.add() 返回的是成功添加的元素数量
                                return redisTemplate.opsForSet().add(redisKey, chatIdStr)
                                        // 确保设置过期时间，防止集合无限增长
                                        .flatMap(count -> redisTemplate.expire(redisKey, CACHE_VALID_DURATION))
                                        .thenReturn(true);
                            })
                            .switchIfEmpty(
                                    // 4. MySQL 未命中
                                    Mono.fromRunnable(() -> log.warn("❌ Chat ID {} not authorized in DB for bot {}", chatId, botConfigId))
                                            .then(Mono.just(false))
                            );
                })
                .onErrorResume(e -> {
                    log.error("🚨 Error during authorization check (Redis/DB) for bot {} and chat {}", botConfigId, chatId, e);
                    // 数据库或 Redis 出现故障时，为了安全，默认拒绝授权
                    return Mono.just(false);
                });

    }
}