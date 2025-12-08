package com.backend.bot.context;

import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.entity.BotConfigEntity;
import lombok.AccessLevel;
import lombok.Getter;

/**
 * 处理器上下文，用于封装和提取所有 Handler 共同需要的核心信息。
 * 这样做可以避免在每个 Handler 的 handle 方法开头重复解析 token, userId, chatId 等。
 */
public record HandlerContext(
        String token,
        String botName,
        String logIdentifier,
        Long userId,
        Long chatId,
        Long messageId
) {
    public HandlerContext(BotConfigEntity botEntity, BotUpdateDto update) {
        this(
                botEntity.getBotToken(),
                botEntity.getBotName(),
                String.format("[%s]", botEntity.getBotName()),

                // 统一提取 userId (可能来自 message 或 callbackQuery)
                update.message() != null
                        ? update.message().from().id()
                        : (update.callbackQuery() != null ? update.callbackQuery().from().id() : null),

                // 统一提取 chatId (可能来自 message 或 callbackQuery.message)
                update.message() != null
                        ? update.message().chat().id()
                        : (update.callbackQuery() != null ? update.callbackQuery().message().chat().id() : null),

                // 统一提取 messageId (可能来自 message 或 callbackQuery.message)
                update.message() != null
                        ? update.message().messageId()
                        : (update.callbackQuery() != null ? update.callbackQuery().message().messageId() : null)
        );

        // 确保关键信息不为空
        if (userId == null || chatId == null) {
            throw new IllegalArgumentException("无法从 BotUpdateDto 中提取用户ID或聊天ID。");
        }
    }
}