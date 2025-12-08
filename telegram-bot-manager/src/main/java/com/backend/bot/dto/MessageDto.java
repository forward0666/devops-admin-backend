package com.backend.bot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

// --- Message DTO ---
public record MessageDto(
        // 映射 Telegram 的 message_id 字段
        @JsonProperty("message_id")
        Long messageId,

        // 映射 Telegram 的 date 字段 (Unix 时间戳)
        @JsonProperty("date")
        Long date,

        // 映射 Telegram 的 chat 字段，用于获取 chatId
        @JsonProperty("chat")
        ChatDto chat,

        // **[新增]** 映射 Telegram 的 from 字段，用于获取 userId
        @JsonProperty("from")
        UserDto from,

        // 映射 Telegram 的 text 字段 (消息文本)
        @JsonProperty("text")
        String text
        // 其他字段（from, entities, photo, caption, reply_to_message 等）可根据需要添加
) {
}