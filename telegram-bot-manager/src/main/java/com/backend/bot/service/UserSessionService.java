package com.backend.bot.service;

import com.backend.bot.entity.UserSessionEntity;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

/**
 * 用户会话状态管理服务接口。
 * 负责用户会话的存储、检索、清理和过期管理。
 */
public interface UserSessionService {

    /**
     * 更新或创建用户会话。
     * @param userId 用户ID
     * @param newState 新的会话状态
     * @param referenceMessageId 关联的消息ID
     * @return 完成信号
     */
    Mono<Void> updateUserSession(Long userId, String newState, Long referenceMessageId);

    /**
     * 获取用户会话。
     * @param userId 用户ID
     * @return 包含会话实体的 Mono
     */
    Mono<UserSessionEntity> getUserSession(Long userId);

    /**
     * 清除用户会话。
     * @param userId 用户ID
     * @return 完成信号
     */
    Mono<Void> clearUserSession(Long userId);

    /**
     * 检查用户是否拥有任何活跃的会话。
     * 用于防止在进行操作时重复触发 /start。
     * @param userId 用户ID
     * @return 包含 Boolean 结果的 Mono
     */
    Mono<Boolean> hasAnySession(Long userId);

    /**
     * 存储待删除任务，用于菜单自动删除。
     * @param userId 用户ID
     * @param deletionTask 待处理任务
     * @return 完成信号
     */
    Mono<Void> storePendingDeletion(Long userId, Disposable deletionTask);

    /**
     * 取消待删除任务。
     * @param userId 用户ID
     * @return 完成信号
     */
    Mono<Void> cancelPendingDeletion(Long userId);
}