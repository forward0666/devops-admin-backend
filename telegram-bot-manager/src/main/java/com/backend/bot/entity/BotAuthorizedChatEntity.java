package com.backend.bot.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 对应数据库 bot_authorized_chat 表
 */
@Data
@Table("bot_authorized_chat")
public class BotAuthorizedChatEntity {

    @Id
    private Long id;

    // 对应数据库字段 bot_config_id
    @Column("bot_config_id")
    private Long botConfigId;

    // 对应数据库字段 chat_id
    @Column("chat_id")
    private Long chatId;

    private String chatName;

    private String type; // private, group, supergroup, etc.

    private Integer status; // 1=启用, 0=禁用

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // 可以在这里添加业务辅助方法，例如：
    public boolean isAuthorized() {
        return status != null && status == 1;
    }
}