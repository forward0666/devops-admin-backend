package com.backend.bot.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BotVo {
    private Long id;
    private String botName;
    private String botUsername;
    private LocalDateTime createdAt;
    private Integer status;

    // 不包含 botToken
}
