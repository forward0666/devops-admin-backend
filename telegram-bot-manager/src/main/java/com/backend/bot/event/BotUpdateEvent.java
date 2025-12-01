package com.backend.bot.event;

import com.backend.bot.dto.BotUpdateDto;

/**
 * 定义 "机器人收到更新" 事件。
 * 包含 Bot 名称和原始更新数据。
 */
public record BotUpdateEvent(
        String botName,
        BotUpdateDto botUpdate
) {}