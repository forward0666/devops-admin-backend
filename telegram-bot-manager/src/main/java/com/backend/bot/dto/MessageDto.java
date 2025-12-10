package com.backend.bot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Telegram 消息数据传输对象
 * 
 * 该类表示 Telegram 平台上的消息对象，包含消息的基本信息和内容。
 * 使用 Java Record 实现不可变数据结构，确保线程安全和数据一致性。
 * 
 * 消息类型支持：
 * 1. 文本消息 - 最常见的消息类型
 * 2. 媒体消息 - 图片、视频、音频等（需要额外字段）
 * 3. 文件消息 - 文档、贴纸等（需要额外字段）
 * 4. 特殊消息 - 位置、联系人、投票等（需要额外字段）
 * 
 * 消息处理流程：
 * 1. 接收 Telegram Webhook 发送的 JSON 数据
 * 2. Jackson 反序列化为 MessageDto 对象
 * 3. 根据消息类型和内容执行相应业务逻辑
 * 4. 根据需要发送回复消息
 * 
 * 设计特点：
 * 1. 使用 Record 实现不可变对象
 * 2. 自动生成 getter 方法（如 messageId(), text()）
 * 3. 自动实现 equals(), hashCode(), toString()
 * 4. 使用 @JsonProperty 注解映射 JSON 字段名
 * 5. 可扩展设计，便于添加新字段支持更多消息类型
 * 
 * @author Backend Team
 * @version 1.0.0
 */
public record MessageDto(
        /**
         * 消息唯一标识符
         * 
         * 在特定聊天中唯一的消息 ID，用于：
         * 1. 引用或回复此消息
         * 2. 编辑发送的消息
         * 3. 删除发送的消息
         * 4. 构建消息链和上下文
         * 
         * 特点：
         * - 在同一聊天中唯一递增
         * - 不同聊天中的消息 ID 可能相同
         * - 与 chatId 结合可全局唯一标识消息
         * - 正数表示用户消息，负数表示 Bot 消息（在某些 API 中）
         * 
         * Telegram API 映射：message_id
         */
        @JsonProperty("message_id")
        Long messageId,

        /**
         * 消息发送时间
         * 
         * 表示消息发送的 Unix 时间戳（秒级精度）。
         * 用于：
         * 1. 显示消息时间
         * 2. 按时间排序消息
         * 3. 计算消息延迟
         * 4. 实现基于时间的业务逻辑
         * 
         * 特点：
         * - Unix 时间戳格式（自 1970-01-01 00:00:00 UTC 的秒数）
         * - UTC 时区，不受时区影响
         * - 可转换为 Instant 或 LocalDateTime 对象
         * 
         * 转换示例：
         * Instant instant = Instant.ofEpochSecond(date);
         * LocalDateTime dateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
         * 
         * Telegram API 映射：date
         */
        @JsonProperty("date")
        Long date,

        /**
         * 聊天对象
         * 
         * 包含消息所属聊天的详细信息。
         * 聊天可以是私聊、群组、超级群组或频道。
         * 
         * 聊天类型：
         * - private: 私人聊天（一对一）
         * - group: 普通群组（最多 200 成员）
         * - supergroup: 超级群组（超过 200 成员）
         * - channel: 频道（单向广播）
         * 
         * 重要性：
         * 1. 获取 chatId 用于发送回复
         * 2. 判断聊天类型应用不同权限
         * 3. 获取聊天标题或用户名用于显示
         * 4. 管理聊天白名单和权限控制
         * 
         * Telegram API 映射：chat
         */
        @JsonProperty("chat")
        ChatDto chat,

        /**
         * 发送者对象
         * 
         * 包含消息发送者的用户信息。
         * 对于频道消息，此字段可能为频道信息而非具体用户。
         * 
         * 发送者信息：
         * - 用户 ID（唯一标识）
         * - 用户名（@username）
         * - 姓名（名和姓）
         * - 是否为 Bot 标志
         * 
         * 重要性：
         * 1. 用户身份验证和授权
         * 2. 个性化回复和用户体验
         * 3. 用户行为分析和统计
         * 4. 用户权限管理和访问控制
         * 5. 维护用户会话状态
         * 
         * Telegram API 映射：from
         */
        @JsonProperty("from")
        UserDto from,

        /**
         * 消息文本内容
         * 
         * 包含消息的文本内容，用于文本消息。
         * 对于非文本消息（如图片、视频），此字段可能为空或包含描述文本。
         * 
         * 文本内容类型：
         * 1. 普通文本 - 用户输入的普通消息
         * 2. 命令 - 以 / 开头的命令（如 /start, /help）
         * 3. 表情符号 - Unicode 表情或自定义表情
         * 4. 链接 - URL 链接或 Telegram 特殊链接
         * 5. 提及 - @username 或 #hashtag
         * 
         * 业务用途：
         * 1. 命令解析和路由
         * 2. 自然语言处理
         * 3. 内容审核和过滤
         * 4. 关键词提取和搜索
         * 5. 多语言支持
         * 
         * 特点：
         * - 可能包含 Markdown 格式
         * - 长度限制（4096 字符）
         * - 支持 Unicode 字符和表情符号
         * 
         * Telegram API 映射：text
         */
        @JsonProperty("text")
        String text
        // 可扩展字段：
        // - entities: 消息实体（粗体、斜体、链接等）
        // - photo: 图片数组（尺寸、文件 ID 等）
        // - caption: 媒体文件的说明文本
        // - reply_to_message: 回复的消息对象
        // - forward_from: 转发的原始消息发送者
        // - location: 位置信息
        // - contact: 联系人信息
        // - document: 文档信息
        // - 等等（根据业务需求逐步添加）
) {
    /**
     * 判断是否为命令消息
     * 
     * 便捷方法，用于判断消息是否为命令（以 / 开头）。
     * 常见命令包括 /start, /help, /settings 等。
     * 
     * @return 如果文本以 / 开头返回 true，否则返回 false
     */
    public boolean isCommand() {
        return text != null && text.startsWith("/");
    }
    
    /**
     * 获取命令名称
     * 
     * 从命令消息中提取命令名称（不含 @username 和参数）。
     * 例如："/start@my_bot param" 返回 "start"
     * 
     * @return 命令名称，如果不是命令则返回 null
     */
    public String getCommandName() {
        if (!isCommand()) {
            return null;
        }
        
        // 移除开头的 /
        String command = text.substring(1);
        
        // 分割命令和可能的参数
        String[] parts = command.split("[\\s@]");
        
        // 返回命令名称部分
        return parts.length > 0 ? parts[0] : null;
    }
    
    /**
     * 获取消息发送时间
     * 
     * 将 Unix 时间戳转换为 Instant 对象，便于时间处理。
     * 
     * @return 消息发送时间的 Instant 对象
     */
    public Instant getSentAt() {
        return date != null ? Instant.ofEpochSecond(date) : null;
    }
}