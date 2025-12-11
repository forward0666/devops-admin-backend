package com.backend.bot.service;

import com.backend.bot.entity.UserSessionEntity;
import com.backend.bot.util.LogUtils; // 假设存在 LogUtils
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 基于内存的会话状态管理服务实现。
 * 注意：在分布式或生产环境中，需要替换为持久化存储（如 Redis）。
 */
@Service
@Profile("dev")
@Slf4j
public class InMemoryUserSessionService implements UserSessionService {

    // 使用 ConcurrentHashMap 存储会话状态：Key=UserId, Value=UserSessionEntity
    private final ConcurrentMap<Long, UserSessionEntity> userSessions = new ConcurrentHashMap<>();

    // 存储菜单自动销毁任务：Key=UserId, Value=Disposable
    // 每个用户同时只能有一个待删除任务
    private final ConcurrentMap<Long, Disposable> pendingDeletions = new ConcurrentHashMap<>();


    @Override
    public Mono<Void> updateUserSession(Long userId, String newState, Long referenceMessageId) {
        return Mono.deferContextual(contextView -> {
            final String logPrefix = LogUtils.prepareMdcAndGetPrefix(contextView);
            return Mono.fromRunnable(() -> {
                UserSessionEntity session = UserSessionEntity.builder()
                        .userId(userId)
                        .currentState(newState)
                        .referenceMessageId(referenceMessageId)
                        .build();

                userSessions.put(userId, session);
                log.info("{}📝 User session updated for userId: {}. New state: {}", logPrefix, userId, newState);
            });
        });
    }

    @Override
    public Mono<UserSessionEntity> getUserSession(Long userId) {
        return Mono.deferContextual(contextView -> {
            final String logPrefix = LogUtils.prepareMdcAndGetPrefix(contextView);
            return Mono.defer(() -> {
                UserSessionEntity session = userSessions.get(userId);
                if (session != null) {
                    log.debug("{}🔎 Found session for userId: {}. State: {}", logPrefix, userId, session.getCurrentState());
                    return Mono.just(session);
                }
                log.debug("{}🔎 No session found for userId: {}", logPrefix, userId);
                return Mono.empty();
            });
        });
    }

    @Override
    public Mono<Void> clearUserSession(Long userId) {
        return Mono.deferContextual(contextView -> {
            final String logPrefix = LogUtils.prepareMdcAndGetPrefix(contextView);
            return Mono.fromRunnable(() -> {
                userSessions.remove(userId);
                log.info("{}🗑️ User session cleared for userId: {}", logPrefix, userId);
            });
        });
    }

    @Override
    public Mono<Void> storePendingDeletion(Long userId, Disposable deletionTask) {
        return Mono.deferContextual(contextView -> {
            final String logPrefix = LogUtils.prepareMdcAndGetPrefix(contextView);
            return Mono.fromRunnable(() -> {
                // 确保旧任务被取消
                cancelPendingDeletion(userId).subscribe();
                pendingDeletions.put(userId, deletionTask);
                log.debug("{}⏳ Stored new deletion task for userId: {}", logPrefix, userId);
            });
        });
    }

    @Override
    public Mono<Void> cancelPendingDeletion(Long userId) {
        return Mono.deferContextual(contextView -> {
            final String logPrefix = LogUtils.prepareMdcAndGetPrefix(contextView);
            return Mono.fromRunnable(() -> {
                Disposable disposable = pendingDeletions.remove(userId);
                if (disposable != null && !disposable.isDisposed()) {
                    disposable.dispose();
                    log.info("{}✅ Cancelled pending menu deletion task for userId: {}", logPrefix, userId);
                } else if (disposable != null) {
                    log.debug("{}⚠️ Attempted to cancel a task that was already disposed for userId: {}", logPrefix, userId);
                }
            });
        });
    }
}