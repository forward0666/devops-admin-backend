package com.backend.bot.service;

import com.backend.bot.entity.UserSessionEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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
            userSessions.remove(userId);
            log.info("🗑️ User session cleared for userId: {}", userId);
        });
    }
}