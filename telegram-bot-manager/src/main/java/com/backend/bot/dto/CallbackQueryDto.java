package com.backend.bot.dto;

// --- Callback Query DTO ---
public record CallbackQueryDto(
        String id,
        MessageDto message,
        String data
) {}
