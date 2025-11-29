package com.backend.bot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

// --- Callback Query DTO ---
public record CallbackQueryDto(
        // 映射 Telegram 的 id 字段 (callback_query_id)
        @JsonProperty("id")
        String id,

        // 映射 Telegram 的 from 字段 (发送者，通常用于获取用户 ID)
        @JsonProperty("from")
        UserDto from,

        // 映射 Telegram 的 message 字段 (原始消息，用于获取 chatId)
        @JsonProperty("message")
        MessageDto message,

        // 映射 Telegram 的 data 字段 (按钮回调数据)
        @JsonProperty("data")
        String data
) {
    // 注意：这里的 UserDto 如果不存在，需要补全。
}