package com.backend.bot.service;

import com.backend.bot.entity.UserSessionEntity;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class InMemoryUserSessionService implements UserSessionService {

    // 使用 ConcurrentHashMap 存储会话状态：Key=UserId, Value=UserSessionEntity
    private final ConcurrentMap<Long, UserSessionEntity> userSessions = new ConcurrentHashMap<>();

    // 🌟 存储待处理的自动删除任务，Key=UserId, Value=Disposable (用于取消任务)
    private final ConcurrentMap<Long, Disposable> pendingDeletions = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> updateUserSession(Long userId, String newState, Long referenceMessageId) {
        return Mono.fromRunnable(() -> {
            UserSessionEntity session = UserSessionEntity.builder()
                    .userId(userId)
                    .currentState(newState)
                    .referenceMessageId(referenceMessageId)
                    .build();

            userSessions.put(userId, session);
            log.info("📝 User session updated for userId: {}. New state: {}", userId, newState);
        });
    }

    @Override
    public Mono<UserSessionEntity> getUserSession(Long userId) {
        return Mono.defer(() -> {
            UserSessionEntity session = userSessions.get(userId);
            if (session != null) {
                log.debug("🔎 Found session for userId: {}. State: {}", userId, session.getCurrentState());
                return Mono.just(session);
            }
            log.debug("🔎 No session found for userId: {}", userId);
            return Mono.empty();
        });
    }

    @Override
    public Mono<Void> clearUserSession(Long userId) {
        return Mono.fromRunnable(() -> {
            // 1. 清除会话状态
            userSessions.remove(userId);
            log.info("🗑️ User session cleared for userId: {}", userId);

            // 2. 确保取消任何待处理的菜单删除任务（防止误删）
            cancelPendingDeletionInternal(userId);
        });
    }

    // --- 自动删除相关方法 ---

    /**
     * 内部方法：取消针对指定用户可能存在的待处理消息自动删除任务。
     */
    private void cancelPendingDeletionInternal(Long userId) {
        Disposable disposable = pendingDeletions.remove(userId);
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
            log.debug("✅ Pending menu deletion canceled for userId: {}", userId);
        }
    }

    /**
     * 🌟 实现 UserSessionService 接口中的方法：取消针对指定用户可能存在的待处理消息自动删除任务。
     */
    @Override
    public Mono<Void> cancelPendingDeletion(Long userId) {
        return Mono.fromRunnable(() -> cancelPendingDeletionInternal(userId));
    }

    /**
     * 供 StartCommandHandler 调用：存储待处理的自动删除任务。
     *
     * @param userId 用户的 Telegram ID
     * @param disposable 自动删除任务的 Disposable 引用
     * @return 一个表示操作完成的 Mono<Void>
     */
    public Mono<Void> storePendingDeletion(Long userId, Disposable disposable) {
        return Mono.fromRunnable(() -> {
            // 确保旧任务被取消
            cancelPendingDeletionInternal(userId);

            pendingDeletions.put(userId, disposable);
            log.debug("⏳ Stored new pending deletion task for userId: {}", userId);
        });
    }
}