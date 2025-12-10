package com.backend.bot.context;

import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.entity.BotConfigEntity;

public record HandlerContext(
        BotConfigEntity botEntity,
        BotUpdateDto update,
        String token,
        String botName,
        String logIdentifier,
        Long userId,
        Long chatId,
        Long messageId,
        String chatTitle,
        String firstName
) {
    public HandlerContext(BotConfigEntity botEntity, BotUpdateDto update) {
        this(
                botEntity,
                update,
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
                        : (update.callbackQuery() != null && update.callbackQuery().message() != null
                        ? update.callbackQuery().message().chat().id() : null),

                // 统一提取 messageId (可能来自 message 或 callbackQuery.message)
                update.message() != null
                        ? update.message().messageId()
                        : (update.callbackQuery() != null && update.callbackQuery().message() != null
                        ? update.callbackQuery().message().messageId() : null),

                // 统一提取 chatTitle
                update.message() != null
                        ? update.message().chat().title()
                        : (update.callbackQuery() != null && update.callbackQuery().message() != null
                        ? update.callbackQuery().message().chat().title() : null),

                // 统一提取 firstName
                update.message() != null
                        ? update.message().from().firstName()
                        : (update.callbackQuery() != null ? update.callbackQuery().from().firstName() : null)
        );

        // 确保关键信息不为空
        if (userId == null || chatId == null) {
            throw new IllegalArgumentException("无法从 BotUpdateDto 中提取用户ID或聊天ID。");
        }
    }
}