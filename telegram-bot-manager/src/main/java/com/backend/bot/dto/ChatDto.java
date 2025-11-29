package com.backend.bot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

// --- Chat DTO (包含 ID) ---
public record ChatDto(
        // 映射 Telegram 的 id 字段 (chat_id)
        @JsonProperty("id")
        Long id,

        // 映射 Telegram 的 type 字段 (private, group, supergroup, channel)
        @JsonProperty("type")
        String type,

        // 映射 Telegram 的 title 字段 (群组/频道名，私聊中不存在)
        @JsonProperty("title")
        String title,

        // 映射 Telegram 的 username 字段
        @JsonProperty("username")
        String username
        // 其他字段（first_name, last_name, bio 等）可根据需要添加
) {
}