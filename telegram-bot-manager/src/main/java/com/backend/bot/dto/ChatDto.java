package com.backend.bot.dto;

/**
 * Telegram API 核心 DTOs，用于解析 Webhook 传入的 JSON 结构。
 * 使用 Java Records (since Java 16) 替代 Lombok 和手动访问器简化结构。
 * Record 自动提供了与组件名匹配的公共访问方法 (如 BotUpdateDto.callbackQuery())，解决了访问权限问题。
 */

// --- Chat DTO ---
public record ChatDto(
        // 字段名与 JSON 匹配，通常不需要 @JsonProperty
        Long id,
        String type // e.g., "private", "group"
) {}
