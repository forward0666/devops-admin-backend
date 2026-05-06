package com.backend.bot.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Table("bot_group")
public class BotGroupEntity {

    @Id
    private Long id;

    private String botName;

    private Long chatId;

    private String chatTitle;

    private String chatType;

    private Long botConfigId;

    private Long projectId;

    private String projectName;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
