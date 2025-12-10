package com.backend.bot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Telegram 聊天数据传输对象
 * 
 * 该类表示 Telegram 平台上的聊天对象，包含聊天的基本信息和类型。
 * 使用 Java Record 实现不可变数据结构，确保线程安全和数据一致性。
 * 
 * 聊天类型：
 * 1. private - 私人聊天（一对一）
 * 2. group - 普通群组（最多 200 成员）
 * 3. supergroup - 超级群组（超过 200 成员）
 * 4. channel - 频道（单向广播）
 * 
 * 聊天用途：
 * 1. 获取聊天 ID 用于发送消息
 * 2. 根据聊天类型应用不同权限和功能
 * 3. 显示聊天名称或用户名
 * 4. 管理聊天白名单和访问控制
 * 5. 个性化用户体验
 * 
 * 设计特点：
 * 1. 使用 Record 实现不可变对象
 * 2. 自动生成 getter 方法（如 id(), type()）
 * 3. 自动实现 equals(), hashCode(), toString()
 * 4. 使用 @JsonProperty 注解映射 JSON 字段名
 * 5. 支持不同聊天类型的差异化处理
 * 
 * @author Backend Team
 * @version 1.0.0
 */
public record ChatDto(
        /**
         * 聊天唯一标识符
         * 
         * Telegram 为每个聊天分配的唯一 ID，用于：
         * 1. 发送消息到指定聊天
         * 2. 识别和跟踪聊天
         * 3. 存储聊天特定数据和设置
         * 4. 实现聊天权限管理
         * 
         * ID 特点：
         * - 在 Telegram 系统中全局唯一
         * - 正数表示用户、群组或频道
         * - 负数表示某些特殊情况下的聊天
         * - 对于私聊，等于用户 ID
         * 
         * 使用场景：
         * - 发送消息 API (sendMessage, editMessageText 等)
         * - 聊白名单管理和授权检查
         * - 用户会话和状态管理
         * - 数据分析和统计
         * 
         * 安全考虑：
         * - 应验证 Bot 是否有权向该聊天发送消息
         * - 敏感操作前应验证聊天类型和权限
         * - 对于未授权聊天应拒绝访问
         * 
         * Telegram API 映射：id
         */
        @JsonProperty("id")
        Long id,

        /**
         * 聊天类型
         * 
         * 表示聊天的类型，决定了可用的功能和权限。
         * 不同类型的聊天有不同的特性和使用场景。
         * 
         * 聊天类型详解：
         * 
         * private（私人聊天）：
         * - 一对一的直接对话
         * - 可以使用所有 Bot 功能
         * - 显示用户的名称而非标题
         * - 适合个性化服务
         * 
         * group（普通群组）：
         * - 最多 200 成员的小型群组
         * - 支持基本的 Bot 功能
         * - 管理员权限可能受限
         * - 适合小团队协作
         * 
         * supergroup（超级群组）：
         * - 超过 200 成员的大型群组
         * - 支持高级功能和权限管理
         * - 可以设置管理员和自定义权限
         * - 适合大型社区和公开讨论
         * 
         * channel（频道）：
         * - 单向广播平台
         * - Bot 只能通过管理员发布消息
         * - 普通用户无法直接与 Bot 交互
         * - 适合新闻发布和通知
         * 
         * 业务应用：
         * 1. 根据类型应用不同权限策略
         * 2. 为不同类型提供定制化功能
         * 3. 实现差异化用户体验
         * 4. 聊天白名单和访问控制
         * 
         * Telegram API 映射：type
         */
        @JsonProperty("type")
        String type,

        /**
         * 聊天标题
         * 
         * 聊天的显示名称，主要用于群组、超级群组和频道。
         * 对于私人聊天，此字段通常为空。
         * 
         * 标题特点：
         * - 群组和频道的显示名称
         * - 可以包含表情符号和特殊字符
         * - 管理员可以随时修改
         * - 私人聊天中通常为空
         * 
         * 使用场景：
         * 1. 在管理界面中显示聊天名称
         * 2. 生成报告和分析数据
         * 3. 个性化回复和通知
         * 4. 聊天白名单管理
         * 
         * 与 username 的区别：
         * - title 是显示名称（如"技术交流群"）
         * - username 是唯一标识符（如"tech_chat"）
         * - title 可能重复，username 在 Telegram 中唯一
         * - title 可以包含中文，username 只能使用拉丁字符
         * 
         * Telegram API 映射：title
         */
        @JsonProperty("title")
        String title,

        /**
         * 聊天用户名
         * 
         * 聊天的公共用户名，格式为 @username。
         * 可用于生成 t.me 链接和在 Telegram 中搜索聊天。
         * 
         * 用户名特点：
         * - 格式：@username（@ 符号不属于用户名本身）
         * - 在 Telegram 中全局唯一
         * - 只能使用拉丁字符、数字和下划线
         * - 长度限制为 5-32 个字符
         * - 私人聊天中，这是用户的用户名
         * 
         * 使用场景：
         * 1. 生成公共链接（t.me/username）
         * 2. 在文本中提及特定聊天
         * 3. 链接到群组或频道
         * 4. 搜索和识别公共聊天
         * 
         * 公开性：
         * - 公共聊天必须有用户名
         * - 私人聊天可能没有用户名
         * - 用户名可以随时更改
         * - 更改后旧链接仍然有效
         * 
         * 与 title 的区别：
         * - username 是唯一标识符（如"tech_chat"）
         * - title 是显示名称（如"技术交流群"）
         * - username 可用于链接和搜索
         * - title 用于显示和识别
         * 
         * Telegram API 映射：username
         */
        @JsonProperty("username")
        String username
        // 可扩展字段：
        // - first_name: 私聊中的用户名字
        // - last_name: 私聊中的用户姓氏
        // - description: 聊天描述（频道和超级群组）
        // - invite_link: 聊天邀请链接
        // - pinned_message: 固定消息
        // - permissions: 默认权限（超级群组）
        // - photo: 聊天照片信息
        // - bio: 聊天简介（频道）
        // 等等（根据业务需求逐步添加）
) {
    /**
     * 判断是否为私人聊天
     * 
     * 便捷方法，判断当前聊天是否为私人聊天（一对一）。
     * 
     * @return 如果是私人聊天返回 true，否则返回 false
     */
    public boolean isPrivate() {
        return "private".equals(type);
    }
    
    /**
     * 判断是否为群组聊天
     * 
     * 便捷方法，判断当前聊天是否为群组（包括普通群组和超级群组）。
     * 
     * @return 如果是群组返回 true，否则返回 false
     */
    public boolean isGroup() {
        return "group".equals(type) || "supergroup".equals(type);
    }
    
    /**
     * 判断是否为频道
     * 
     * 便捷方法，判断当前聊天是否为频道。
     * 
     * @return 如果是频道返回 true，否则返回 false
     */
    public boolean isChannel() {
        return "channel".equals(type);
    }
    
    /**
     * 获取显示名称
     * 
     * 根据聊天类型返回最合适的显示名称。
     * 对于私人聊天，可能需要从其他字段获取用户名。
     * 
     * @return 优先返回 title，其次返回 username，都为空返回 null
     */
    public String getDisplayName() {
        if (title != null && !title.isEmpty()) {
            return title;
        }
        if (username != null && !username.isEmpty()) {
            return "@" + username;
        }
        return null;
    }
    
    /**
     * 获取公共链接
     * 
     * 如果聊天有用户名，生成 t.me 公共链接。
     * 
     * @return 公共链接，如果没有用户名返回 null
     */
    public String getPublicLink() {
        return username != null && !username.isEmpty() 
                ? "https://t.me/" + username 
                : null;
    }
}