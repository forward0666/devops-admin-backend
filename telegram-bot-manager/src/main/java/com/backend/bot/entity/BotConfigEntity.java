package com.backend.bot.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.MappedCollection; // 导入 MappedCollection
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.Set; // 导入 Set

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

    // 🚀 明确一对多关系：一个 BotConfig 对应多个 Authorized Chats
    // keyColumn = "bot_config_id" 指示关联表 (bot_authorized_chat) 中指向本表主键的字段名。
    @MappedCollection(keyColumn = "bot_config_id")
    private Set<BotAuthorizedChatEntity> authorizedChats; // 关联的授权聊天列表

    // 可以在这里添加一些业务辅助方法
    public boolean isActive() {
        return status != null && status == 1;
    }
}