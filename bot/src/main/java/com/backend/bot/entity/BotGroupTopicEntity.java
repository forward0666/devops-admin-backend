package com.backend.bot.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Table("bot_group_topic")
public class BotGroupTopicEntity {

    @Id
    private Long id;

    private String botName;

    private Long chatId;

    private Long threadId;

    private String topicName;

    private LocalDateTime createdAt;
}
