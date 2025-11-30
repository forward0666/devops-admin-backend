package com.backend.bot.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Table("bot_config")
public class BotConfigEntity {
    @Id
    private Long id;
    private String botName;
    private String botUsername;
    private String botType;
    private String botToken;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 可以在这里添加一些业务辅助方法
    public boolean isActive() {
        return status != null && status == 1;
    }
}