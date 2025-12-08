package com.backend.bot.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BotVo {
    private Long id;
    private String botName;
    private String botUsername;
    private Boolean active;
    private LocalDateTime createdAt;
    // 不包含 botToken
}
