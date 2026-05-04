package com.backend.bot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Telegram 回调查询数据传输对象
 * 
 * 该类表示用户点击内联按钮后产生的回调查询对象。
 * 回调查询是 Bot 实现交互式菜单和复杂操作流程的核心机制。
 * 使用 Java Record 实现不可变数据结构，确保线程安全和数据一致性。
 * 
 * 回调查询流程：
 * 1. Bot 发送带有内联键盘的消息
 * 2. 用户点击其中一个按钮
 * 3. Telegram 向 Bot 发送回调查询
 * 4. Bot 解析回调数据并执行相应操作
 * 5. Bot 可选择显示提示或更新原始消息
 * 
 * 应用场景：
 * 1. 菜单导航系统
 * 2. 多步骤表单填写
 * 3. 确认对话框
 * 4. 数据查询和筛选
 * 5. 设置和配置界面
 * 
 * 设计特点：
 * 1. 使用 Record 实现不可变对象
 * 2. 自动生成 getter 方法（如 id(), data()）
 * 3. 自动实现 equals(), hashCode(), toString()
 * 4. 使用 @JsonProperty 注解映射 JSON 字段名
 * 5. 包含原始消息引用，支持上下文操作
 * 
 * @author Backend Team
 * @version 1.0.0
 */
public record CallbackQueryDto(
        /**
         * 回调查询唯一标识符
         * 
         * Telegram 为每个回调查询分配的唯一 ID，用于：
         * 1. 回答回调查询（显示提示消息）
         * 2. 标识和跟踪特定回调查询
         * 3. 调试和日志记录
         * 
         * 使用场景：
         * - answerCallbackQuery API 调用
         * - 确认用户操作（如"操作成功"提示）
         * - 显示错误消息或警告
         * - 弹出临时提示（不修改原始消息）
         * 
         * 特点：
         * - 字符串类型，包含字母和数字
         * - 全局唯一，不同查询有不同的 ID
         * - 只能使用一次，过期后无效
         * 
         * Telegram API 映射：id
         */
        @JsonProperty("id")
        String id,

        /**
         * 触发回调查询的用户
         * 
         * 包含点击按钮的用户信息，用于：
         * 1. 用户身份验证和授权
         * 2. 个性化响应和用户体验
         * 3. 用户会话管理和状态跟踪
         * 4. 权限检查和访问控制
         * 
         * 用户信息包含：
         * - 用户 ID（唯一标识符）
         * - 用户名（@username）
         * - 姓名信息（名和姓）
         * - 是否为 Bot 标志
         * 
         * 安全考虑：
         * - 应验证用户是否有权执行相应操作
         * - 区分不同用户的数据和设置
         * - 防止跨用户数据访问
         * 
         * Telegram API 映射：from
         */
        @JsonProperty("from")
        UserDto from,

        /**
         * 触发回调查询的原始消息
         * 
         * 包含用户点击按钮的原始消息对象，用于：
         * 1. 获取聊天 ID 用于发送响应
         * 2. 修改或更新原始消息内容
         * 3. 构建消息上下文和操作历史
         * 4. 实现消息状态的动态更新
         * 
         * 原始消息包含：
         * - 消息 ID（用于编辑消息）
         * - 聊天信息（用于确定回复目标）
         * - 消息内容和键盘布局
         * - 发送时间和其它元数据
         * 
         * 更新原始消息：
         * 使用 editMessageText、editMessageReplyMarkup 等 API
         * 可以修改消息文本、键盘或两者同时修改
         * 
         * 注意：
         * - 对于来自内联模式的回调查询，此字段可能为空
         * - 频道中的回调查询可能包含频道信息而非具体消息
         * 
         * Telegram API 映射：message
         */
        @JsonProperty("message")
        MessageDto message,

        /**
         * 按钮回调数据
         * 
         * 包含按钮点击时发送的自定义数据，用于：
         * 1. 识别用户操作意图
         * 2. 传递操作参数
         * 3. 维护应用状态和上下文
         * 4. 实现多步骤操作流程
         * 
         * 数据格式设计：
         * - 常用格式：action:param1:param2:...
         * - 示例：menu:navigation:settings
         * - 示例：ip_whitelist:add:192.168.1.1
         * - 示例：confirm:operation_id
         * 
         * 解析策略：
         * 1. 按分隔符分割获取操作和参数
         * 2. 根据操作类型路由到相应处理器
         * 3. 参数验证和类型转换
         * 4. 执行业务逻辑并更新状态
         * 
         * 设计考虑：
         * - 长度限制为 1-64 字节
         * - 只支持 UTF-8 字符
         * - 应设计紧凑但可读的格式
         * - 避免包含敏感信息
         * 
         * Telegram API 映射：data
         */
        @JsonProperty("data")
        String data
) {
    /**
     * 获取聊天 ID
     * 
     * 便捷方法，从原始消息中提取聊天 ID。
     * 如果消息为空（如来自内联模式的回调查询），返回 null。
     * 
     * @return 聊天 ID，如果无法获取则返回 null
     */
    public Long getChatId() {
        return message != null && message.chat() != null ? message.chat().id() : null;
    }
    
    /**
     * 获取用户 ID
     * 
     * 便捷方法，从用户对象中提取用户 ID。
     * 
     * @return 用户 ID，如果用户为空则返回 null
     */
    public Long getUserId() {
        return from != null ? from.id() : null;
    }
    
    /**
     * 解析回调操作
     * 
     * 从回调数据中提取操作部分（第一个冒号之前的部分）。
     * 例如：从 "menu:navigation:settings" 提取 "menu"。
     * 
     * @return 操作字符串，如果数据为空则返回 null
     */
    public String getAction() {
        if (data == null || data.isEmpty()) {
            return null;
        }
        
        int colonIndex = data.indexOf(':');
        return colonIndex > 0 ? data.substring(0, colonIndex) : data;
    }
    
    /**
     * 解析回调参数
     * 
     * 从回调数据中提取参数部分（第一个冒号之后的部分）。
     * 例如：从 "menu:navigation:settings" 提取 "navigation:settings"。
     * 
     * @return 参数字符串，如果没有参数则返回 null
     */
    public String getParameters() {
        if (data == null || data.isEmpty()) {
            return null;
        }
        
        int colonIndex = data.indexOf(':');
        return colonIndex > 0 && colonIndex < data.length() - 1 
                ? data.substring(colonIndex + 1) 
                : null;
    }
    
    /**
     * 获取参数数组
     * 
     * 将回调数据按冒号分割为字符串数组。
     * 例如：将 "menu:navigation:settings" 分割为 ["menu", "navigation", "settings"]。
     * 
     * @return 参数数组，如果数据为空则返回空数组
     */
    public String[] getParameterArray() {
        if (data == null || data.isEmpty()) {
            return new String[0];
        }
        
        return data.split(":");
    }
}