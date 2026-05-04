package com.backend.bot.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * Bot 授权聊天实体类
 * 
 * 该类映射到数据库的 bot_authorized_chat 表，用于存储每个 Bot 授权访问的聊天列表。
 * 该实体实现了白名单机制，确保只有授权的聊天可以与 Bot 进行交互。
 * 
 * 设计特点：
 * 1. 作为 BotConfigEntity 的子实体，建立一对多关联关系
 * 2. 支持多种聊天类型（私聊、群组、超级群组等）
 * 3. 包含状态字段管理聊天授权状态
 * 4. 使用 Spring Data R2DBC 进行响应式数据库操作
 * 
 * 数据表结构：
 * - id: 主键，自增长
 * - bot_config_id: 外键，关联到 bot_config 表的主键
 * - chat_id: Telegram 聊天 ID（唯一标识）
 * - chat_name: 聊天名称（可变，用于显示）
 * - type: 聊天类型（private, group, supergroup, channel）
 * - status: 授权状态（0-禁用，1-启用）
 * - created_at: 创建时间
 * - updated_at: 更新时间
 * 
 * 设计说明：
 * 保留 botConfigId 字段非常重要，它不仅用于实体关联，还支持
 * Spring Data R2DBC 的方法名解析查询，如 findByBotConfigIdAndChatId。
 * 
 * @author Backend Team
 * @version 1.0.0
 */
@Data                         // Lombok 注解，自动生成 getter/setter、toString 等方法
@Table("bot_authorized_chat") // 映射到数据库表 bot_authorized_chat
public class BotAuthorizedChatEntity {

    /**
     * 主键 ID
     * 
     * 使用 @Id 注解标记为主键，由数据库自动生成。
     * 确保每条授权聊天记录的唯一性。
     */
    @Id
    private Long id;

    /**
     * Bot 配置 ID
     * 
     * 外键字段，关联到 bot_config 表的主键。
     * 
     * 设计要点：
     * - 必须保留此字段以支持 Repository 方法名解析查询
     * - 用于建立 Bot 与授权聊天的一对多关系
     * - 支持查询如 findByBotConfigIdAndChatId 的方法
     * - 与 @MappedCollection 注解的 keyColumn 属性对应
     */
    @Column("bot_config_id")
    private Long botConfigId;

    /**
     * Telegram 聊天 ID
     * 
     * Telegram 系统中聊天的唯一标识符，用于识别特定聊天。
     * 
     * 特点：
     * - 在 Telegram 系统中全局唯一
     * - 对于私聊是用户 ID
     * - 对于群组是群组 ID
     * - 用于 API 调用中的聊天目标识别
     */
    @Column("chat_id")
    private Long chatId;

    /**
     * 聊天名称
     * 
     * 聊天的可读名称，用于在管理界面中显示。
     * 
     * 说明：
     * - 对于私聊，是用户的显示名称
     * - 对于群组，是群组名称
     * - 该字段可能会变化，用于显示而非识别
     */
    private String chatName;

    /**
     * 聊天类型
     * 
     * Telegram 聊天的类型分类，用于不同类型聊天的特殊处理。
     * 
     * 可能的值：
     * - private: 私人聊天（一对一）
     * - group: 普通群组
     * - supergroup: 超级群组
     * - channel: 频道
     */
    private String type; // private, group, supergroup, channel, etc.

    /**
     * 授权状态
     * 
     * 整数类型状态码，管理聊天是否被授权访问 Bot。
     * 
     * 状态定义：
     * - 0: 禁用状态（未授权或已被撤销授权）
     * - 1: 启用状态（已授权，可以与 Bot 交互）
     */
    private Integer status; // 1=启用, 0=禁用

    /**
     * 创建时间
     * 
     * 记录聊天授权的创建时间，由 R2DBC 审计功能自动管理。
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     * 
     * 记录聊天授权信息的最后更新时间，由 R2DBC 审计功能自动管理。
     */
    private LocalDateTime updatedAt;

    /**
     * 检查聊天是否已授权
     * 
     * 业务辅助方法，用于判断聊天是否处于授权状态。
     * 
     * @return 如果状态不为空且等于1则返回true，表示聊天已授权；否则返回false
     */
    public boolean isAuthorized() {
        return status != null && status == 1;
    }
}