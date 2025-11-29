package com.backend.bot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

// --- Message DTO ---
public record MessageDto(
        @JsonProperty("message_id") Long messageId,
        Long date,
        String text,
        ChatDto chat
) {}
