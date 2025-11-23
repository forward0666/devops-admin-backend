package com.backend.bot.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Table("bot_config")
public class BotEntity {
    @Id
    private Long id;
    private String botName;
    private String botUsername;
    private String botType;
    private String botToken;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}