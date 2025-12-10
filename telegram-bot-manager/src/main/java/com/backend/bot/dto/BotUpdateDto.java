package com.backend.bot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Telegram Bot 更新数据传输对象
 * 
 * 该类是 Telegram Bot API 更新的顶级容器，用于接收和处理来自 Telegram 的所有更新事件。
 * 使用 Java Record 实现不可变数据结构，确保线程安全和数据一致性。
 * 
 * Telegram 更新类型：
 * 1. Message - 普通消息（文本、图片、文件等）
 * 2. CallbackQuery - 内联按钮回调查询
 * 3. EditedMessage - 编辑的消息
 * 4. ChannelPost - 频道帖子
 * 5. 其他类型（如 InlineQuery、ChosenInlineResult 等）
 * 
 * 更新处理流程：
 * 1. Telegram Webhook 发送 JSON 数据到我们的服务器
 * 2. Jackson 框架将 JSON 反序列化为 BotUpdateDto 对象
 * 3. 根据更新类型调用相应的处理器
 * 4. 处理完成后返回响应
 * 
 * 设计特点：
 * 1. 使用 Record 实现不可变对象
 * 2. 自动生成 getter 方法（如 updateId(), message()）
 * 3. 自动实现 equals(), hashCode(), toString()
 * 4. 使用 @JsonProperty 注解映射 JSON 字段名
 * 5. message 和 callbackQuery 互斥，同一时间只有一个不为空
 * 
 * @author Backend Team
 * @version 1.0.0
 */
public record BotUpdateDto(
        /**
         * 更新唯一标识符
         * 
         * Telegram 为每个更新分配的唯一递增 ID。
         * 用于：
         * 1. 标识更新顺序，确保按序处理
         * 2. 避免重复处理相同的更新
         * 3. 实现更新偏移量管理
         * 
         * 特点：
         * - 严格递增，每个 Bot 都有自己的 ID 序列
         * - 长期为 Long 类型，支持大量更新
         * - 在获取更新时可用作 offset 参数
         * 
         * Telegram API 映射：update_id
         */
        @JsonProperty("update_id")
        Long updateId,

        /**
         * 消息对象
         * 
         * 表示用户发送的普通消息，包括文本、图片、文件等。
         * 当用户直接向 Bot 发送消息时，此字段不为空。
         * 
         * 消息类型包括：
         * - 文本消息
         * - 图片、视频、音频等媒体
         * - 文件、贴纸等
         * - 位置信息、联系人等
         * 
         * 注意：
         * - 与 callbackQuery 互斥，同一时间只有一个不为空
         * - 可能包含命令（如 /start, /help）
         * - 包含发送者和聊天信息
         * 
         * Telegram API 映射：message
         */
        @JsonProperty("message")
        MessageDto message,

        /**
         * 回调查询对象
         * 
         * 表示用户点击内联按钮产生的回调查询。
         * 当用户与 Bot 的交互式菜单交互时，此字段不为空。
         * 
         * 回调特点：
         * - 由内联键盘按钮触发
         * - 包含回调数据，可用于状态管理
         * - 可以显示提示消息或更新原始消息
         * - 支持链式交互（多步操作）
         * 
         * 使用场景：
         * - 菜单导航
         * - 表单提交
         * - 确认对话框
         * - 多步骤操作流程
         * 
         * 注意：
         * - 与 message 互斥，同一时间只有一个不为空
         * - 包含触发按钮的原始消息引用
         * - 回调数据可用于确定用户意图
         * 
         * Telegram API 映射：callback_query
         */
        @JsonProperty("callback_query")
        CallbackQueryDto callbackQuery
) {
    /**
     * 判断更新是否为消息类型
     * 
     * 便捷方法，用于快速判断当前更新是否为消息类型，
     * 避免在业务代码中重复进行 null 检查。
     * 
     * @return 如果 message 不为空返回 true，否则返回 false
     */
    public boolean isMessage() {
        return message != null;
    }
    
    /**
     * 判断更新是否为回调查询类型
     * 
     * 便捷方法，用于快速判断当前更新是否为回调查询类型，
     * 简化业务代码的条件判断。
     * 
     * @return 如果 callbackQuery 不为空返回 true，否则返回 false
     */
    public boolean isCallbackQuery() {
        return callbackQuery != null;
    }
}