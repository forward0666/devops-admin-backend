package com.backend.bot.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient; // 导入 Transient
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.Set;

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

    // 🚀 修复方案：添加 @Transient
    // 告诉 R2DBC 在执行 SELECT * FROM bot_config 查询时，忽略这个字段。
    // R2DBC 的级联加载需要手动配置或使用特殊的 Repository 方法来触发，
    // 否则在单表查询时，它不应被包含在 SELECT 列表中。
    @Transient
    @MappedCollection(keyColumn = "bot_config_id")
    private Set<BotAuthorizedChatEntity> authorizedChats;

    // 可以在这里添加一些业务辅助方法
    public boolean isActive() {
        return status != null && status == 1;
    }
}