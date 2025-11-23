package com.backend.bot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

// TgChat 聊天对象
public record BotChatDto(
        @JsonProperty("id") Long id,
        @JsonProperty("type") String type, // private, group, supergroup
        @JsonProperty("title") String title
) {}
