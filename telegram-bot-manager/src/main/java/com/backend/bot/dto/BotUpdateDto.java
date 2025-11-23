package com.backend.bot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

// TgUpdate 根对象
public record BotUpdateDto(
        @JsonProperty("update_id") Long updateId,
        @JsonProperty("message") BotMessageDto message
) {}

