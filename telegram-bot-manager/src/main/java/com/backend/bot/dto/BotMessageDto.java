package com.backend.bot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

// TgMessage 消息体
public record BotMessageDto(
        @JsonProperty("message_id") Long messageId,
        @JsonProperty("chat") BotChatDto chat,
        @JsonProperty("text") String text
) {}
