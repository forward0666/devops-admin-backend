package com.backend.bot.entity;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;

/**
 * 用户的会话状态实体，用于存储多步操作中的上下文信息。
 */
@Data
@Builder
@ToString
public class UserSessionEntity {
    /** 用户的 Telegram ID */
    private final Long userId;

    /** 当前会话状态，如 AWAITING_FRONTEND_IP */
    private final String currentState;

    /** 触发当前状态的原始消息 ID */
    private final Long referenceMessageId;

    /** 状态设置的时间戳 */
    private final long timestamp = System.currentTimeMillis();

    /** * 由于 UserSessionEntity 被定义为 Record/Builder 风格的 DTO，
     * 并且 TextUpdateHandler 需要 getState() 方法，这里明确提供它。
     */
    public String getState() {
        return currentState;
    }
}