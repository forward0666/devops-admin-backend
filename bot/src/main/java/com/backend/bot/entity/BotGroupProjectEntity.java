package com.backend.bot.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Table("bot_group_project")
public class BotGroupProjectEntity {

    @Id
    private Long id;

    private String botName;

    private Long chatId;

    private String chatTitle;

    private Long projectId;

    private String projectName;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
