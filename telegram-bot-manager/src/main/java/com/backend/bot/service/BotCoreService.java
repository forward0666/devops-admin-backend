package com.backend.bot.service;

import com.backend.bot.dto.BotRegisterDto;
import com.backend.bot.entity.BotConfigEntity; // 统一使用 BotConfigEntity
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


    /**
     * 缓存 Bot 实体的 Key 格式，为 botName 和 ID 组合留出空间
     */
    private static final String CACHE_WHITELIST_PREFIX = "bot:whitelist:set:";

    /**
     * 注册新 Bot
     *
     * @param dto 包含 Bot 注册信息的 DTO
     * @return 注册后的 BotVo
     */
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
    /**
     * 根据 Bot 名称查询 Bot 实体
     *
     * @param botName Bot 名称
     * @return BotConfigEntity 或空 Mono
     */
    public Mono<BotConfigEntity> findByBotName(String botName) {
        String entityCacheKey = CACHE_ENTITY_PREFIX + botName;

        return redisTemplate.opsForValue().get(entityCacheKey)
                .flatMap(json -> {
                    if ("INVALID".equals(json)) {
                        log.debug("💬 Bot name cache marked as INVALID: {}", botName);
                        return Mono.empty();
                    }

                    // 1. 在阻塞线程池中执行反序列化
                    return Mono.fromCallable(() -> {
                                try {
                                    return objectMapper.readValue(json, BotConfigEntity.class);
                                } catch (JsonProcessingException e) {
                                    // 2. 详细记录反序列化失败的日志
                                    log.error("🚨 Failed to deserialize BotConfigEntity from Redis for name: {}, attempting DB lookup", botName, e);
                                    // 3. 将 checked exception 转换为 RuntimeException 抛出
                                    throw new RuntimeException(e);
                                }
                            })
                            .subscribeOn(Schedulers.boundedElastic()) // 切换线程池
                            // 4. 仅在成功时记录命中日志
                            .doOnSuccess(entity -> log.debug("💬 Bot entity cache hit for name: {}", botName));
                })
                // 5. 统一处理从 fromCallable 抛出的所有 RuntimeException (包括序列化失败)
                .onErrorResume(RuntimeException.class, e -> {
                    return Mono.empty();
                })
                // 6. 缓存未命中：查询数据库
                .switchIfEmpty(
                        botRepository.findByBotName(botName)
                                .flatMap(this::cacheBotEntity)
                                .switchIfEmpty(
                                        // 7. 数据库中不存在：缓存 INVALID 标记
                                        redisTemplate.opsForValue().set(entityCacheKey, "INVALID", CACHE_INVALID_DURATION)
                                                .then(Mono.empty())
                                )
                );
    }

    /**
     * 辅助方法：将 BotConfigEntity 响应式缓存到 Redis (只缓存 Bot Name 键)
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
            log.error("🚨 Failed to serialize BotConfigEntity for caching", e); // 确保日志中提及 BotConfigEntity
            return Mono.just(entity);
        }
    }
    /**
     * 将 BotConfigEntity 转换为 BotVo
     *
     * @param botEntity BotConfigEntity 实体
     * @return 转换后的 BotVo
     */
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
     * @param botName     机器人的友好名称/中文描述（用于 Redis Key 可读性）
     * @param chatId      要验证的聊天ID
     * @return Mono<Boolean> - true 如果允许，false 如果不允许。
     */
    public Mono<Boolean> isChatIdAuthorized(Long botConfigId, String botName, Long chatId) {
        if (botConfigId == null || chatId == null) {
            log.warn("🚨 Auth check failed: Missing botConfigId or chatId. BotName: {}", botName);
            return Mono.just(false);
        }

        // 核心修改：Redis Key 包含 botName 和 botConfigId，提高可读性
        // 示例 Key: bot:whitelist:set:测试白名单开发机器人:1
        String redisKey = CACHE_WHITELIST_PREFIX + botName + ":" + botConfigId;
        String chatIdStr = String.valueOf(chatId);

        // 1. 尝试从 Redis Set 中检查是否存在
        return redisTemplate.opsForSet().isMember(redisKey, chatIdStr)
                .flatMap(isMember -> {
                    if (Boolean.TRUE.equals(isMember)) {
                        log.debug("✅ Chat ID {} cache hit in Redis whitelist for bot {} (Key: {})", chatId, botName, redisKey);
                        return Mono.just(true); // Redis 命中，授权成功
                    }

                    // 2. Redis 未命中：查询 MySQL
                    log.debug("🔍 Chat ID {} not found in Redis. Checking MySQL for bot {} (ID: {})", chatId, botName, botConfigId);
                    return authorizedChatRepository.findByBotConfigIdAndChatId(botConfigId, chatId)
                            .flatMap(entity -> {
                                log.info("✅ Chat ID {} found in MySQL whitelist. Caching to Redis {} (ID: {}).", chatId, botName, botConfigId);

                                // 3. MySQL 命中：缓存到 Redis Set
                                // OpsForSet.add() 返回的是成功添加的元素数量
                                return redisTemplate.opsForSet().add(redisKey, chatIdStr)
                                        // 确保设置过期时间，防止集合无限增长
                                        .flatMap(count -> redisTemplate.expire(redisKey, CACHE_VALID_DURATION))
                                        .thenReturn(true);
                            })
                            .switchIfEmpty(
                                    // 4. MySQL 未命中
                                    Mono.fromRunnable(() -> log.warn("❌ Chat ID {} not authorized in DB for bot {} (ID: {})", chatId, botName, botConfigId))
                                            .then(Mono.just(false))
                            );
                })
                .onErrorResume(e -> {
                    log.error("🚨 Error during authorization check (Redis/DB) for bot {} (ID: {}) and chat {}", botName, botConfigId, chatId, e);
                    // 数据库或 Redis 出现故障时，为了安全，默认拒绝授权
                    return Mono.just(false);
                });

    }

    /**
     * 缓存 Bot 实体的 Key 格式，为 botName 和 ID 组合留出空间
     *
     * @param botName Bot 名称
     * @param botId Bot ID
     * @return 缓存 Key
     */
    private String getCacheEntityKey(String botName, String botId) {
        return CACHE_ENTITY_PREFIX + botName + ":" + botId;
    }

    /**
     * 缓存 Bot 实体的 Key 格式，为 botName 和 ID 组合留出空间
     *
     * @param botName Bot 名称
     * @return 缓存 Key
     */
    private String getCacheWhitelistKey(String botName) {
        return CACHE_WHITELIST_PREFIX + botName;
    }
}