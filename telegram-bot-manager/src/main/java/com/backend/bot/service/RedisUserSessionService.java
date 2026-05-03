package com.backend.bot.service;

import com.backend.bot.entity.UserSessionEntity;
import com.backend.bot.util.LogUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于 Redis 的会话状态管理服务实现。
 * 用于在多实例部署环境中共享用户会话状态。
 */
@Service
@Primary
@RequiredArgsConstructor
@Slf4j
public class RedisUserSessionService implements UserSessionService {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // 存储菜单自动销毁任务：Key=UserId, Value=Disposable
    // 这部分仍然在内存中，因为 Disposable 对象无法序列化到 Redis
    private final ConcurrentMap<Long, Disposable> pendingDeletions = new ConcurrentHashMap<>();
    
    // 存储系统消息（userId=0）的自动删除任务：Key=UniqueId, Value=Disposable
    // 避免多个系统消息互相覆盖，使用原子计数器生成唯一ID
    private final ConcurrentMap<Long, Disposable> systemMessageDeletions = new ConcurrentHashMap<>();
    private final AtomicLong systemMessageCounter = new AtomicLong(0);

    // Redis Key 前缀
    private static final String SESSION_KEY_PREFIX = "telegram:session:";

    // 会话过期时间（5分钟）
    private static final Duration SESSION_TTL = Duration.ofMinutes(5);

    /**
     * 构建 Redis Key
     */
    private String buildSessionKey(Long userId) {
        return SESSION_KEY_PREFIX + userId;
    }

    // 移除了 buildUserIdKey，因为它不再使用

    @Override
    public Mono<Void> updateUserSession(Long userId, String newState, Long referenceMessageId) {
        return Mono.deferContextual(contextView -> {
            final String logPrefix = getLogPrefix(contextView);

            try {
                UserSessionEntity session = UserSessionEntity.builder()
                        .userId(userId)
                        .currentState(newState)
                        .referenceMessageId(referenceMessageId)
                        .build();

                String sessionKey = buildSessionKey(userId);
                String sessionJson = objectMapper.writeValueAsString(session);

                log.info("{}📝 Storing user session in Redis for userId: {}. New state: {}", logPrefix, userId, newState);

                // 存储会话并设置过期时间
                return redisTemplate.opsForValue()
                        .set(sessionKey, sessionJson, SESSION_TTL)
                        .then()
                        .onErrorResume(e -> {
                            log.error("{}❌ Failed to store user session in Redis for userId: {}. Error: {}", logPrefix, userId, e.getMessage(), e);
                            return Mono.empty();
                        });
            } catch (JsonProcessingException e) {
                log.error("{}❌ Failed to serialize user session for userId: {}. Error: {}", logPrefix, userId, e.getMessage(), e);
                return Mono.empty();
            }
        });
    }

    @Override
    public Mono<UserSessionEntity> getUserSession(Long userId) {
        return Mono.deferContextual(contextView -> {
            final String logPrefix = getLogPrefix(contextView);
            String sessionKey = buildSessionKey(userId);

            return redisTemplate.opsForValue().get(sessionKey)
                    .map(sessionJson -> {
                        try {
                            UserSessionEntity session = objectMapper.readValue(sessionJson, UserSessionEntity.class);
                            log.debug("{}🔎 Found session in Redis for userId: {}. State: {}", logPrefix, userId, session.getCurrentState());
                            return session;
                        } catch (JsonProcessingException e) {
                            log.error("{}❌ Failed to deserialize user session from Redis for userId: {}. Error: {}", logPrefix, userId, e.getMessage(), e);
                            // 反序列化失败，应清除该键
                            // FIX: 导入 Context 并使用 Context.of(contextView) 修复找不到符号的问题
                            clearUserSession(userId).contextWrite(Context.of(contextView)).subscribe();
                            return null;
                        }
                    })
                    .filter(session -> session != null)
                    .switchIfEmpty(Mono.defer(() -> {
                        log.debug("{}🔎 No session found in Redis for userId: {}", logPrefix, userId);
                        return Mono.empty();
                    }));
        });
    }

    /**
     * 检查用户是否有任何活跃会话
     * FIX: 只需要检查主会话键 (telegram:session:{userId}) 是否存在。
     * @param userId 用户ID
     * @return Mono&lt;Boolean&gt; 如果用户有会话返回 true，否则返回 false
     */
    @Override
    public Mono<Boolean> hasAnySession(Long userId) {
        return Mono.deferContextual(contextView -> {
            final String logPrefix = getLogPrefix(contextView);
            String sessionKey = buildSessionKey(userId);

            log.debug("{}🔍 Checking if user {} has any session by primary key: {}", logPrefix, userId, sessionKey);

            return redisTemplate.hasKey(sessionKey)
                    .defaultIfEmpty(false)
                    .doOnNext(hasKey -> {
                        if (hasKey) {
                            log.debug("{}✅ User {} has an active session.", logPrefix, userId);
                        } else {
                            log.debug("{}❌ User {} has no active session.", logPrefix, userId);
                        }
                    })
                    .onErrorResume(e -> {
                        log.error("{}❌ Failed to check primary session key for userId: {}. Assuming no session (Safety fall-through).", logPrefix, userId, e);
                        return Mono.just(false);
                    });
        });
    }

    @Override
    public Mono<Void> clearUserSession(Long userId) {
        return Mono.deferContextual(contextView -> {
            final String logPrefix = getLogPrefix(contextView);
            String sessionKey = buildSessionKey(userId);

            // 只需要删除主会话键
            log.info("{}🗑️ Clearing primary user session key from Redis for userId: {}", logPrefix, userId);

            return redisTemplate.delete(sessionKey)
                    .then()
                    .onErrorResume(e -> {
                        log.error("{}❌ Failed to clear user session from Redis for userId: {}. Error: {}", logPrefix, userId, e.getMessage(), e);
                        return Mono.empty();
                    });
        });
    }

    @Override
    public Mono<Void> storePendingDeletion(Long userId, Disposable deletionTask) {
        return Mono.deferContextual(contextView -> {
            final String logPrefix = getLogPrefix(contextView);

            if (userId == 0L) {
                // 对于系统消息（userId=0），使用唯一的计数器值作为键
                // 不取消之前的系统消息，让它们独立运行
                long uniqueId = systemMessageCounter.incrementAndGet();
                systemMessageDeletions.put(uniqueId, deletionTask);
                log.info("{}⏳ Stored new system message deletion task in memory with uniqueId: {}", logPrefix, uniqueId);
                return Mono.empty();
            } else {
                // 确保旧任务被取消
                return cancelPendingDeletion(userId)
                        .then(Mono.fromRunnable(() -> {
                            pendingDeletions.put(userId, deletionTask);
                            log.debug("{}⏳ Stored new deletion task in memory for userId: {}", logPrefix, userId);
                        }));
            }
        });
    }

    @Override
    public Mono<Void> cancelPendingDeletion(Long userId) {
        return Mono.deferContextual(contextView -> {
            final String logPrefix = getLogPrefix(contextView);

            if (userId == 0L) {
                // 对于系统消息（userId=0），取消所有系统消息的删除任务
                log.info("{}⚠️ Cancelling all system message deletion tasks", logPrefix);
                systemMessageDeletions.forEach((id, disposable) -> {
                    if (disposable != null && !disposable.isDisposed()) {
                        disposable.dispose();
                        log.debug("{}✅ Cancelled system message deletion task for uniqueId: {}", logPrefix, id);
                    }
                });
                systemMessageDeletions.clear();
                return Mono.empty();
            } else {
                return Mono.fromRunnable(() -> {
                    Disposable disposable = pendingDeletions.remove(userId);
                    if (disposable != null && !disposable.isDisposed()) {
                        disposable.dispose();
                        log.info("{}✅ Cancelled pending menu deletion task for userId: {}", logPrefix, userId);
                    } else if (disposable != null) {
                        log.debug("{}⚠️ Attempted to cancel a task that was already disposed for userId: {}", logPrefix, userId);
                    }
                });
            }
        });
    }

    /**
     * 从上下文中提取日志前缀
     */
    private String getLogPrefix(ContextView contextView) {
        try {
            // 使用 LogUtils 来统一处理 traceId 的获取和格式化
            return LogUtils.prepareMdcAndGetPrefix(contextView);
        } catch (Exception e) {
            return "[traceId=N/A]";
        }
    }
}