package com.backend.bot.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Bot 类型枚举类
 * 
 * 该枚举定义了系统中支持的各种 Telegram Bot 类型，每种类型对应不同的功能和用途。
 * 使用枚举而非字符串常量提供了类型安全性和代码可读性。
 * 
 * 支持的 Bot 类型：
 * 1. IP_WHITE_LIST: IP 白名单管理 Bot，用于管理服务器访问控制
 * 2. CUSTOMER_SERVICE: 客服 Bot，用于处理用户咨询和客户服务
 * 3. TOOL: 工具 Bot，提供各种实用工具功能
 * 
 * 技术特性：
 * 1. 使用 @JsonValue 注解实现自定义 JSON 序列化
 * 2. 与 R2DBC 转换器配合实现数据库存储
 * 3. 类型安全的枚举设计，避免字符串拼写错误
 * 4. 可扩展的设计，便于添加新的 Bot 类型
 * 
 * @author Backend Team
 * @version 1.0.0
 */
public enum BotType {
    
    /**
     * 通用 Bot
     */
    GENERAL("general"),

    /**
     * IP 白名单管理 Bot
     * 
     * 功能：用于管理系统和服务器的 IP 白名单
     * 
     * 主要用途：
     * - 添加/删除白名单 IP
     * - 查看当前白名单状态
     * - IP 访问权限管理
     * - 安全策略实施
     * 
     * 数据库存储值：ip_white_list
     */
    IP_WHITE_LIST("ip_white_list"),
    
    /**
     * 客服 Bot
     * 
     * 功能：处理用户咨询、投诉和客户服务工作
     * 
     * 主要用途：
     * - 自动回复常见问题
     * - 转接人工客服
     * - 收集用户反馈
     * - 知识库查询
     * 
     * 数据库存储值：customer_service
     */
    CUSTOMER_SERVICE("customer_service"),
    
    /**
     * 工具 Bot
     * 
     * 功能：提供各种实用工具和辅助功能
     * 
     * 主要用途：
     * - 系统监控和状态查询
     * - 数据查询和报表生成
     * - 运维自动化工具
     * - 开发辅助工具
     * 
     * 数据库存储值：tool
     */
    TOOL("tool");

    /**
     * 数据库存储值
     * 
     * 每个枚举项对应的数据库存储字符串值，用于：
     * 1. R2DBC 数据库存储和检索
     * 2. Redis 缓存序列化
     * 3. HTTP API 响应序列化
     * 4. 日志记录和调试
     */
    private final String dbValue;

    /**
     * 构造函数
     * 
     * 初始化枚举项，设置对应的数据库存储值。
     * 
     * @param dbValue 数据库中存储的字符串值
     */
    BotType(String dbValue) {
        this.dbValue = dbValue;
    }

    /**
     * 获取数据库存储值
     * 
     * 提供枚举项对应的数据库存储值。
     * 
     * 使用 @JsonValue 注解告诉 Jackson 序列化框架：
     * 1. 在 JSON 序列化时使用此方法的返回值
     * 2. 在 JSON 反序列化时根据此值匹配对应的枚举
     * 3. 应用于 Redis 存储和 HTTP API 响应
     * 4. 与自定义的 R2DBC 转换器配合工作
     * 
     * 设计优势：
     * - 支持数据库存储友好的值（如 snake_case）
     * - 允许枚举名称与存储值分离
     * - 便于未来数据库迁移和国际化
     * 
     * @return 数据库存储字符串值
     */
    public String getDbValue() {
        return dbValue;
    }

    public static BotType fromDbValue(String value) {
        for (BotType type : values()) {
            if (type.dbValue.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid bot type: " + value);
    }
}