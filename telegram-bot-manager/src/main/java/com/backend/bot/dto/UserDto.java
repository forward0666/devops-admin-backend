package com.backend.bot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

// --- User DTO (From) ---
public record UserDto(
        // 映射 Telegram 的 id 字段 (用户 ID)
        @JsonProperty("id")
        Long id,

        // 映射 Telegram 的 is_bot 字段
        @JsonProperty("is_bot")
        Boolean isBot,

        // 映射 Telegram 的 first_name 字段
        @JsonProperty("first_name")
        String firstName
        // 其他字段（username, language_code 等）可根据需要添加
) {
}