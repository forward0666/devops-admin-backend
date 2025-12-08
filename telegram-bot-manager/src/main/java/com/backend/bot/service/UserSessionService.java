package com.backend.bot.service;

import com.backend.bot.entity.UserSessionEntity;
import reactor.core.publisher.Mono;

/**
 * 用户会话状态管理服务接口。
 * 负责存储和检索用户在多步操作中的当前状态。
 */
public interface UserSessionService {

    /**
     * 更新用户的会话状态。
     *
     * @param userId 用户的 Telegram ID
     * @param newState 新的会话状态（例如：AWAITING_FRONTEND_IP）
     * @param referenceMessageId 触发该状态的原始消息 ID，用于追踪
     * @return 一个表示操作完成的 Mono<Void>
     */
    Mono<Void> updateUserSession(Long userId, String newState, Long referenceMessageId);

    /**
     * 获取指定用户的当前会话状态。
     *
     * @param userId 用户的 Telegram ID
     * @return 包含当前状态的 Mono<UserSessionEntity>
     */
    Mono<UserSessionEntity> getUserSession(Long userId);

    /**
     * 清除指定用户的会话状态。
     *
     * @param userId 用户的 Telegram ID
     * @return 一个表示操作完成的 Mono<Void>
     */
    Mono<Void> clearUserSession(Long userId);
}