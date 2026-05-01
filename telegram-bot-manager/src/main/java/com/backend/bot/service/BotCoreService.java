package com.backend.bot.service;

import com.backend.bot.dto.BotRegisterDto;
import com.backend.bot.entity.BotAuthorizedChatEntity;
import com.backend.bot.entity.BotConfigEntity; // 统一使用 BotConfigEntity
import com.backend.bot.repository.BotRepository; // 假设 BotRepository 存在
import com.backend.bot.vo.BotVo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler; // 🌟 导入 Scheduler
import com.backend.bot.repository.BotAuthorizedChatRepository;
import com.backend.bot.config.TelegramProperties; // <--- 新增导入
import java.time.Duration;

@Service
@Slf4j
public class BotCoreService {

    private final BotRepository botRepository;
    private final BotClientService botClient;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final BotAuthorizedChatRepository authorizedChatRepository;
    private final Scheduler blockingTaskScheduler; // 🌟 新增 Scheduler 字段

    // 🌟 新增 CacheTemplateService 依赖
    private final CacheTemplateService cacheTemplateService;
    private final TelegramProperties telegramProperties;

    private static final Duration CACHE_VALID_DURATION = Duration.ofHours(1);
    private static final Duration CACHE_INVALID_DURATION = Duration.ofMinutes(5);
    private static final String CACHE_ENTITY_PREFIX = "bot:entity:name:";
    private static final String CACHE_WHITELIST_PREFIX = "bot:authorizedchat:set:";
    private static final String CACHE_KEY_SEPARATOR = ":"; // 统一键分隔符

    // 🌟 显式构造函数，注入所有依赖，包括自定义 Scheduler 和 CacheTemplateService
    public BotCoreService(
            BotRepository botRepository,
            BotClientService botClient,
            ReactiveStringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            BotAuthorizedChatRepository authorizedChatRepository,
            @Qualifier("blockingTaskScheduler") Scheduler blockingTaskScheduler,
            CacheTemplateService cacheTemplateService,
            TelegramProperties telegramProperties) {
        this.botRepository = botRepository;
        this.botClient = botClient;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.authorizedChatRepository = authorizedChatRepository;
        this.blockingTaskScheduler = blockingTaskScheduler;
        this.cacheTemplateService = cacheTemplateService;
        this.telegramProperties = telegramProperties;
    }

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
                    String webhookUrl = telegramProperties.getWebhookDomain() + "/callback/" + savedConfig.getBotName();
                    String secret = dto.getSecretToken() != null && !dto.getSecretToken().isBlank()
                            ? dto.getSecretToken()
                            : java.util.UUID.randomUUID().toString();
                    return botClient.setWebhook(savedConfig.getBotToken(), webhookUrl, secret)
                            .thenReturn(savedConfig);
                })
                .flatMap(this::cacheBotEntity) // 3. 响应式缓存
                .map(this::convertToVo);
    }

    /**
     * 【重构】根据 Bot 名称查询 Bot 实体 (使用 CacheTemplateService)
     *
     * @param botName Bot 名称
     * @return BotConfigEntity 或空 Mono
     */
    public Mono<BotConfigEntity> findByBotName(String botName) {

        // 1. 统一生成缓存 Key
        String cacheKey = getBotEntityCacheKey(botName);

        // 2. 定义数据库查询逻辑
        Mono<BotConfigEntity> dbFetcher = botRepository.findByBotName(botName);

        // 3. 调用通用的 Value 旁路缓存模板
        return cacheTemplateService.getValueOrFetch(
                cacheKey,
                CACHE_VALID_DURATION,
                CACHE_INVALID_DURATION,
                dbFetcher,
                BotConfigEntity.class
        );
    }

    /**
     * 辅助方法：生成 Bot 实体缓存 Key
     * 格式: bot:entity:name:<botName>
     */
    private String getBotEntityCacheKey(String botName) {
        return CACHE_ENTITY_PREFIX + botName;
    }

    /**
     * 辅助方法：将 BotConfigEntity 响应式缓存到 Redis (只缓存 Bot Name 键)
     * 此方法现在仅处理 Bot 注册时的初始缓存，逻辑已简化。
     */
    private Mono<BotConfigEntity> cacheBotEntity(BotConfigEntity entity) {
        if (entity == null) return Mono.empty();

        String entityKey = getBotEntityCacheKey(entity.getBotName());

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
            log.error("🚨 Failed to serialize BotConfigEntity for caching", e);
            return Mono.just(entity);
        }
    }

    /**
     * 将 BotConfigEntity 转换为 BotVo
     *
     * @param botEntity BotConfigEntity 实体
     * @return 转换后的 BotVo
     */
    public BotVo convertToVo(BotConfigEntity botEntity) {
        BotVo vo = new BotVo();
        if (botEntity != null) {
            vo.setId(botEntity.getId());
            vo.setBotName(botEntity.getBotName());
            vo.setBotUsername(botEntity.getBotUsername());
            vo.setStatus(botEntity.getStatus());
            vo.setCreatedAt(botEntity.getCreatedAt());
        }
        return vo;
    }

    /**
     * 获取所有 Bot 列表
     */
    public reactor.core.publisher.Flux<BotVo> findAll() {
        return botRepository.findAll()
                .map(this::convertToVo);
    }

    /**
     * 根据名称删除 Bot
     */
    public Mono<Boolean> deleteByBotName(String botName) {
        return botRepository.findByBotName(botName)
                .flatMap(bot -> botRepository.delete(bot).then(Mono.just(true)))
                .defaultIfEmpty(false);
    }

    /**
     * 保存 Bot 配置
     */
    public Mono<BotConfigEntity> saveBot(BotConfigEntity bot) {
        return botRepository.save(bot);
    }

    /**
     * 【重构】异步检查指定的 Chat ID 是否在 Bot 的白名单中 (使用 CacheTemplateService)
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

        // 1. 统一生成缓存 Key
        String cacheKey = getWhitelistCacheKey(botName, botConfigId);
        String chatIdStr = String.valueOf(chatId);

        // 2. 定义数据库查询逻辑
        Mono<Boolean> dbFetcher = authorizedChatRepository.findByBotConfigIdAndChatId(botConfigId, chatId)
                .map(BotAuthorizedChatEntity::isAuthorized) // 检查记录是否存在且status=1(已授权)
                .defaultIfEmpty(false); // 如果记录不存在，返回false

        // 3. 调用通用的 Set 旁路缓存模板
        return cacheTemplateService.checkSetMembershipOrFetch(
                cacheKey,
                chatIdStr,
                CACHE_VALID_DURATION,
                dbFetcher
        );
    }

    /**
     * 辅助方法：生成白名单缓存 Key
     * 格式: bot:whitelist:set:<botName>:<botConfigId>
     *
     * @param botName Bot 名称
     * @param botConfigId Bot 配置 ID
     * @return 缓存 Key
     */
    private String getWhitelistCacheKey(String botName, Long botConfigId) {
        // 确保 botName 不包含 : 字符，防止 key 结构混乱
        String sanitizedBotName = botName.replace(CACHE_KEY_SEPARATOR, "_");
        return CACHE_WHITELIST_PREFIX + sanitizedBotName + CACHE_KEY_SEPARATOR + botConfigId;
    }

    /**
     * 更新授权聊天状态并更新缓存
     * 
     * @param botName Bot 名称
     * @param botConfigId Bot 配置 ID
     * @param chatId 聊天ID
     * @param status 新状态 (0=禁用, 1=启用)
     * @return Mono<Boolean> - true 如果更新成功，false 如果记录不存在
     */
    public Mono<Boolean> updateChatAuthorizationStatus(String botName, Long botConfigId, Long chatId, Integer status) {
        log.info("📝 Updating authorization for bot {} (ID: {}), chat {} to status: {}", botName, botConfigId, chatId, status);
        
        // 1. 查询并更新数据库中的记录
        return authorizedChatRepository.findByBotConfigIdAndChatId(botConfigId, chatId)
                .flatMap(entity -> {
                    entity.setStatus(status);
                    return authorizedChatRepository.save(entity);
                })
                .flatMap(savedEntity -> {
                    // 2. 更新缓存
                    String cacheKey = getWhitelistCacheKey(botName, botConfigId);
                    String chatIdStr = String.valueOf(chatId);
                    
                    if (status == 1) {
                        // 启用授权：添加到缓存
                        return redisTemplate.opsForSet().add(cacheKey, chatIdStr)
                                .flatMap(count -> redisTemplate.expire(cacheKey, CACHE_VALID_DURATION))
                                .thenReturn(true)
                                .doOnSuccess(v -> log.info("✅ Chat {} authorization enabled and cached for bot {}", chatId, botName));
                    } else {
                        // 禁用授权：从缓存中移除
                        return redisTemplate.opsForSet().remove(cacheKey, chatIdStr)
                                .thenReturn(true)
                                .doOnSuccess(v -> log.info("✅ Chat {} authorization disabled and removed from cache for bot {}", chatId, botName));
                    }
                })
                .defaultIfEmpty(false) // 记录不存在返回false
                .doOnSuccess(success -> {
                    if (!success) {
                        log.warn("⚠️ Failed to update authorization status for bot {}, chat {} - record not found", botName, chatId);
                    }
                })
                .doOnError(e -> log.error("❌ Error updating authorization status for bot {}, chat {}", botName, chatId, e));
    }
    
    /**
     * 添加新的授权聊天
     * 
     * @param botName Bot 名称
     * @param botConfigId Bot 配置 ID
     * @param chatId 聊天ID
     * @param chatName 聊天名称（可选）
     * @param type 聊天类型（private, group, supergroup等）
     * @return Mono<Boolean> - true 如果添加成功，false 如果已存在
     */
    public Mono<Boolean> addAuthorizedChat(String botName, Long botConfigId, Long chatId, String chatName, String type) {
        log.info("📝 Adding authorized chat {} for bot {} (ID: {})", chatId, botName, botConfigId);
        
        // 1. 检查是否已存在
        return authorizedChatRepository.findByBotConfigIdAndChatId(botConfigId, chatId)
                .hasElement()
                .flatMap(exists -> {
                    if (exists) {
                        log.warn("⚠️ Chat {} already authorized for bot {}", chatId, botName);
                        return Mono.just(false);
                    }
                    
                    // 2. 创建新记录
                    BotAuthorizedChatEntity entity = new BotAuthorizedChatEntity();
                    entity.setBotConfigId(botConfigId);
                    entity.setChatId(chatId);
                    entity.setChatName(chatName);
                    entity.setType(type);
                    entity.setStatus(1); // 默认启用
                    
                    // 3. 保存到数据库
                    return authorizedChatRepository.save(entity)
                            .flatMap(savedEntity -> {
                                // 4. 更新缓存
                                String cacheKey = getWhitelistCacheKey(botName, botConfigId);
                                String chatIdStr = String.valueOf(chatId);
                                
                                return redisTemplate.opsForSet().add(cacheKey, chatIdStr)
                                        .flatMap(count -> redisTemplate.expire(cacheKey, CACHE_VALID_DURATION))
                                        .thenReturn(true)
                                        .doOnSuccess(v -> log.info("✅ Chat {} authorized and cached for bot {}", chatId, botName));
                            });
                })
                .doOnError(e -> log.error("❌ Error adding authorized chat {} for bot {}", chatId, botName, e));
    }
    
    /**
     * 根据Bot名称更新Bot状态并清除缓存
     * 
     * @param botName Bot名称
     * @param status 新状态
     * @return Mono<BotConfigEntity> 更新后的实体，如果记录不存在则返回 Mono.empty()
     */
    public Mono<BotConfigEntity> updateBotStatusByName(String botName, Integer status) {
        log.info("📝 Updating bot {} status to: {}", botName, status);
        return botRepository.findByBotName(botName)
                .flatMap(entity -> {
                    entity.setStatus(status);
                    return botRepository.save(entity);
                })
                .flatMap(savedEntity -> {
                    // 清除缓存，确保下次查询获取最新状态
                    return clearBotCache(savedEntity.getBotName()).thenReturn(savedEntity);
                })
                .doOnSuccess(v -> log.info("✅ Successfully updated bot {} status to: {}", botName, status))
                .doOnError(e -> log.error("❌ Failed to update bot {} status: {}", botName, e.getMessage(), e));
    }

    /**
     * 根据Bot ID查询Bot实体
     * 
     * @param id Bot ID
     * @return BotConfigEntity 或空 Mono
     */
    public Mono<BotConfigEntity> findByBotId(Long id) {
        return botRepository.findById(id);
    }

    /**
     * 清除指定Bot的缓存
     * 
     * @param botName Bot名称
     * @return Mono<Void>
     */
    private Mono<Void> clearBotCache(String botName) {
        String cacheKey = getBotEntityCacheKey(botName);
        return redisTemplate.delete(cacheKey).then()
                .doOnSuccess(v -> log.debug("✅ Cleared cache for bot: {}", botName))
                .doOnError(e -> log.error("❌ Failed to clear cache for bot {}: {}", botName, e.getMessage(), e));
    }

    // ❌ 移除未使用的 getCacheEntityKey(String botName, String botId)
    // ❌ 移除未使用的 getCacheWhitelistKey(String botName)
}