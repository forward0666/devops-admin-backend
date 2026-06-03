package com.backend.bot.entity;

import com.backend.bot.enums.BotType;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Bot 配置实体类
 * 
 * 该类映射到数据库的 bot_config 表，用于存储 Telegram Bot 的配置信息。
 * 作为核心实体，它包含了 Bot 的基本信息、类型、认证状态等关键数据。
 * 
 * 设计特点：
 * 1. 使用 Spring Data R2DBC 进行响应式数据库操作
 * 2. 采用枚举类型提高类型安全性
 * 3. 支持一对一的关联关系（Bot - 授权聊天列表）
 * 4. 包含审计字段自动管理创建和更新时间
 * 
 * 数据表结构：
 * - id: 主键，自增长
 * - bot_name: Bot 显示名称
 * - bot_username: Bot 用户名（@username）
 * - bot_type: Bot 类型（枚举值，存储为字符串）
 * - bot_token: Bot 认证令牌
 * - status: Bot 状态（0-禁用，1-启用）
 * - created_at: 创建时间
 * - updated_at: 更新时间
 * 
 * @author Backend Team
 * @version 1.0.0
 */
@Data                          // Lombok 注解，自动生成 getter/setter、toString 等方法
@Table("bot_config")           // 映射到数据库表 bot_config
public class BotConfigEntity {
    
    /**
     * 主键 ID
     * 
     * 使用 @Id 注解标记为主键，由数据库自动生成。
     * 在 R2DBC 中，主键通常为自增长类型，确保每条记录的唯一性。
     */
    @Id
    private Long id;
    
    /**
     * Bot 显示名称
     * 
     * 存储用户自定义的 Bot 显示名称，用于在界面和管理系统中显示。
     * 这不是 Telegram 的用户名，而是内部管理系统中的名称。
     */
    private String botName;
    
    /**
     * Bot 用户名
     * 
     * 存储在 Telegram 上的唯一用户名，格式为 @username。
     * 用于在 Telegram 平台上识别和访问 Bot。
     */
    private String botUsername;
    
    /**
     * Bot 类型
     * 
     * 使用枚举类型代替字符串，提高类型安全性和代码可读性。
     * 支持的 Bot 类型：
     * - GENERAL: 通用 Bot
     * - ALERT: 告警 Bot
     */
    private BotType botType;
    
    /**
     * Bot 认证令牌
     * 
     * 从 BotFather 获取的唯一认证令牌，用于调用 Telegram API。
     * 这是敏感信息，在数据库中应该加密存储。
     */
    private String botToken;
    
    /**
     * Bot 状态
     * 
     * 整数类型状态码：
     * - 0: 禁用状态
     * - 1: 启用状态
     * 
     * 可以根据业务需求扩展更多状态值。
     */
    private Integer status;

    private String webhookUrl;

    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     * 
     * 记录 Bot 配置的最后更新时间，由 R2DBC 审计功能自动管理。
     * 使用 @LastModifiedDate 注解在 Repository 层自动更新。
     */
    private LocalDateTime updatedAt;

    /**
     * 授权聊天列表
     * 
     * 该字段映射到 bot_authorized_chat 表，表示该 Bot 授权的聊天列表。
     * 使用 @Transient 注解避免在单表查询时包含此字段，
     * 使用 @MappedCollection 注解配置一对多关联关系。
     * 
     * R2DBC 级联加载特性：
     * - 需要手动触发加载或使用特定的 Repository 方法
     * - 不会自动包含在单表查询的 SELECT 语句中
     * - 支持延迟加载和按需加载
     */
    @Transient
    @MappedCollection(keyColumn = "bot_config_id")
    private Set<BotAuthorizedChatEntity> authorizedChats;

    /**
     * 检查 Bot 是否处于活动状态
     * 
     * 业务辅助方法，用于判断 Bot 是否处于启用状态。
     * 
     * @return 如果状态不为空且等于1则返回true，表示Bot处于活动状态；否则返回false
     */
    public boolean isActive() {
        return status != null && status == 1;
    }
}