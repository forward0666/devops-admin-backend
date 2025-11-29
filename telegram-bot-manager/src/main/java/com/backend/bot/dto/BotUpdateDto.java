package com.backend.bot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;


// --- 顶级 Update DTO ---
public record BotUpdateDto(
        // 映射 Telegram 的 update_id 字段
        @JsonProperty("update_id")
        Long updateId,

        // 映射 Telegram 的 message 字段
        @JsonProperty("message")
        MessageDto message,

        // 映射 Telegram 的 callback_query 字段
        @JsonProperty("callback_query")
        CallbackQueryDto callbackQuery
) {
    // 由于使用了 Records，现在 BotController 可以直接调用 update.message() 和 update.callbackQuery()
    // 这些方法是 Record 自动生成的。
}