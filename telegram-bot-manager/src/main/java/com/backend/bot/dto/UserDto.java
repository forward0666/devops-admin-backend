package com.backend.bot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Telegram 用户数据传输对象
 * 
 * 该类表示 Telegram 平台上的用户对象，包含用户的基本信息和属性。
 * 使用 Java Record 实现不可变数据结构，确保线程安全和数据一致性。
 * 
 * 用户类型：
 * 1. 普通用户 - 真实的 Telegram 用户账户
 * 2. Bot 用户 - 自动化程序账户
 * 
 * 用户信息用途：
 * 1. 用户身份验证和授权
 * 2. 个性化服务和用户体验
 * 3. 用户行为分析和统计
 * 4. 用户权限管理和访问控制
 * 5. 多语言支持和本地化
 * 
 * 设计特点：
 * 1. 使用 Record 实现不可变对象
 * 2. 自动生成 getter 方法（如 id(), firstName()）
 * 3. 自动实现 equals(), hashCode(), toString()
 * 4. 使用 @JsonProperty 注解映射 JSON 字段名
 * 5. 支持用户和 Bot 的差异化处理
 * 
 * @author Backend Team
 * @version 1.0.0
 */
public record UserDto(
        /**
         * 用户唯一标识符
         * 
         * Telegram 为每个用户分配的唯一 ID，用于：
         * 1. 识别和跟踪用户
         * 2. 用户身份验证和授权
         * 3. 存储用户特定数据和设置
         * 4. 实现用户权限管理
         * 
         * ID 特点：
         * - 在 Telegram 系统中全局唯一
         * - 一旦分配永不改变
         * - 正数表示用户，负数可能表示特殊类型
         * - 对于私聊，ChatDto.id 等于 UserDto.id
         * 
         * 使用场景：
         * - 用户白名单管理
         * - 用户会话状态管理
         * - 个性化数据存储
         * - 用户权限和访问控制
         * - 数据分析和用户统计
         * 
         * 安全考虑：
         * - 应验证用户权限后执行敏感操作
         * - 谨慎处理用户数据，遵守隐私法规
         * - 对于未授权用户应限制功能访问
         * - 用户 ID 应加密存储在日志中
         * 
         * Telegram API 映射：id
         */
        @JsonProperty("id")
        Long id,

        /**
         * 是否为 Bot 标志
         * 
         * 指示当前用户是否为 Bot 而非真实用户。
         * 用于区分不同类型的账户和应用相应的业务逻辑。
         * 
         * 标志含义：
         * - true: Bot 用户（自动化程序）
         * - false: 普通用户（真实个人）
         * - null: 未知或未指定（某些情况下）
         * 
         * Bot 特点：
         * - 不能被添加到普通群组（只能被添加到超级群组）
         * - 可以使用 Bot API 而不是用户 API
         * - 没有在线状态和最后上线时间
         * - 不能发起对话，只能回复
         * 
         * 应用场景：
         * 1. 根据用户类型应用不同的处理逻辑
         * 2. 限制或允许特定功能
         * 3. 统计和监控不同类型用户的活动
         * 4. 实现 Bot 之间的交互
         * 
         * 业务处理：
         * - 拒绝来自某些 Bot 的请求（如果需要）
         * - 对普通用户提供完整功能
         * - 对管理员 Bot 提供高级功能
         * - 记录不同类型用户的行为模式
         * 
         * Telegram API 映射：is_bot
         */
        @JsonProperty("is_bot")
        Boolean isBot,

        /**
         * 用户名字
         * 
         * 用户的名字（first name），是用户身份的基本组成部分。
         * 在 Telegram 中，每个用户必须有名字，而姓氏和用户名是可选的。
         * 
         * 名字特点：
         * - 1-64 个字符
         * - 支持 Unicode 字符和表情符号
         * - 可以随时修改
         * - 在私聊中通常是显示名称的一部分
         * 
         * 使用场景：
         * 1. 个性化称呼和问候
         * 2. 在界面中显示用户名称
         * 3. 生成日志和报告
         * 4. 个性化消息和通知
         * 
         * 显示逻辑：
         * - 通常与姓氏组合显示
         * - 如果没有姓氏，单独显示名字
         * - 可以结合用户名提供唯一标识
         * 
         * 多语言支持：
         * - 支持各种语言和字符集
         * - 可以包含表情符号
         * - 在某些语言中可能包含多个单词
         * 
         * Telegram API 映射：first_name
         */
        @JsonProperty("first_name")
        String firstName,

        /**
         * 用户用户名
         * 
         * 用户的公共用户名，格式为 @username。
         * 用户名在 Telegram 中是唯一的，可用于识别和链接到用户。
         * 
         * 用户名特点：
         * - 格式：@username（@ 符号不属于用户名本身）
         * - 在 Telegram 中全局唯一
         * - 只能使用拉丁字符、数字和下划线
         * - 长度限制为 5-32 个字符
         * - 用户名是可选的，不是所有用户都有
         * 
         * 使用场景：
         * 1. 生成用户链接（t.me/username）
         * 2. 在文本中提及特定用户
         * 3. 用户身份识别和验证
         * 4. 作为登录名或标识符
         * 
         * 公开性：
         * - 用户名是公开的，任何人都可以搜索
         * - 可以通过设置限制谁可以通过用户名找到用户
         * - 可以随时更改，但旧链接可能失效
         * 
         * 与其他字段的关系：
         * - 与名字组合提供完整的用户标识
         * - 在某些情况下可以替代 ID 用于识别
         * - 可以用于构建用户档案和社交链接
         * 
         * Telegram API 映射：username
         */
        @JsonProperty("username")
        String username
        // 可扩展字段：
        // - last_name: 用户姓氏
        // - language_code: 用户语言代码（如 zh-CN）
        // - is_premium: 是否为 Premium 用户
        // - added_to_attachment_menu: 是否添加到附件菜单
        // - can_join_groups: 是否可以加入群组（仅 Bot）
        // - can_read_all_group_messages: 是否可以读取所有群组消息（仅 Bot）
        // - supports_inline_queries: 是否支持内联查询（仅 Bot）
        //  等等（根据业务需求逐步添加）
) {
    /**
     * 获取完整姓名
     * 
     * 组合名字和姓氏，返回用户的全名。
     * 如果只有名字，则只返回名字。
     * 
     * @return 用户全名，如果名字为空则返回 null
     */
    public String getFullName() {
        if (firstName == null) {
            return null;
        }
        
        // 如果有姓氏，组合名和姓
        // 注意：当前记录中没有 last_name 字段，如果需要可以添加
        return firstName;
    }
    
    /**
     * 获取显示名称
     * 
     * 根据可用信息返回最适合的显示名称。
     * 优先级：全名 > 名字 > 用户名
     * 
     * @return 显示名称，如果都为空则返回 null
     */
    public String getDisplayName() {
        String fullName = getFullName();
        if (fullName != null && !fullName.isEmpty()) {
            return fullName;
        }
        
        if (username != null && !username.isEmpty()) {
            return "@" + username;
        }
        
        return null;
    }
    
    /**
     * 获取公共链接
     * 
     * 如果用户有用户名，生成 t.me 公共链接。
     * 
     * @return 公共链接，如果没有用户名返回 null
     */
    public String getPublicLink() {
        return username != null && !username.isEmpty() 
                ? "https://t.me/" + username 
                : null;
    }
    
    /**
     * 判断是否为普通用户
     * 
     * 便捷方法，判断当前用户是否为普通用户（非 Bot）。
     * 
     * @return 如果不是 Bot 或 isBot 为 null 返回 true，否则返回 false
     */
    public boolean isRegularUser() {
        return !Boolean.TRUE.equals(isBot);
    }
}