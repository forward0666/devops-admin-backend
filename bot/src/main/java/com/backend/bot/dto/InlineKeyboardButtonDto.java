package com.backend.bot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * Telegram 内联键盘按钮数据传输对象
 * 
 * 该类表示 Telegram 内联键盘中的单个按钮，是构建交互式菜单的基本组件。
 * 内联按钮附加在消息下方，点击后会触发回调查询或其他动作。
 * 
 * 按钮类型：
 * 1. 回调按钮 - 点击后发送回调查询（最常用）
 * 2. URL 按钮 - 点击后打开网页
 * 3. 内联查询按钮 - 点击后在当前聊天中插入内联查询
 * 4. 切换内联按钮 - 点击后在当前聊天中切换到内联模式
 * 5. 登录按钮 - 点击后进行授权登录
 * 
 * 应用场景：
 * 1. 交互式菜单导航
 * 2. 多步骤操作流程
 * 3. 确认对话框
 * 4. 数据查询和筛选
 * 5. 外部链接集成
 * 
 * 设计特点：
 * 1. 使用传统类而非 Record，支持更灵活的字段组合
 * 2. 使用 Lombok 简化 getter/setter 生成
 * 3. 支持多种按钮类型的字段
 * 4. 使用 @JsonProperty 注解映射 JSON 字段名
 * 5. 提供便捷的构造函数
 * 
 * @author Backend Team
 * @version 1.0.0
 */
@Getter                      // Lombok 注解，自动生成 getter 方法
@Setter                      // Lombok 注解，自动生成 setter 方法
public class InlineKeyboardButtonDto {
    
    /**
     * 按钮显示文本
     * 
     * 按钮上显示的文本内容，用于向用户说明按钮的功能。
     * 
     * 文本特点：
     * - 必填字段，每个按钮都必须有文本
     * - 长度限制为 1-64 字符
     * - 支持 Markdown 格式（在某些 API 中）
     * - 可以包含表情符号
     * 
     * 设计原则：
     * - 文本应该简洁明了，清楚表达按钮功能
     * - 使用动词和名词的组合（如"添加 IP"、"返回菜单"）
     * - 保持一致性，相同功能使用相同的文本
     * - 考虑多语言支持
     * 
     * 示例：
     * - "添加到白名单"
     * - "返回主菜单"
     * - "查看详情"
     * - "确认操作"
     * 
     * Telegram API 映射：text
     */
    private String text;
    
    /**
     * 回调数据
     * 
     * 用户点击按钮后发送给 Bot 的数据，用于识别用户操作和传递参数。
     * 这是最常用的按钮类型，适用于大多数交互场景。
     * 
     * 数据格式：
     * - 字符串类型，长度限制为 1-64 字节
     * - 支持 UTF-8 编码
     * - 可设计为结构化格式（如 action:param1:param2）
     * - 应包含足够信息用于处理操作，但避免过于复杂
     * 
     * 常用格式设计：
     * - 操作名：action_name
     * - 操作+参数：action_name:param1:param2
     * - 层级+操作：menu:level1:action
     * - ID+操作：id:123:action
     * 
     * 示例：
     * - "menu:navigation:settings" - 导航到设置菜单
     * - "ip_whitelist:add:192.168.1.1" - 添加 IP 到白名单
     * - "confirm:delete:item_123" - 确认删除项目 123
     * 
     * 注意：
     * - 如果使用了 callback_data，则不能同时使用 url、switch_inline_query 等其他字段
     * - 数据应该设计为易于解析的结构
     * - 避免在回调数据中包含敏感信息
     * 
     * Telegram API 映射：callback_data
     */
    @JsonProperty("callback_data")
    private String callbackData;
    
    // 可扩展字段（按需添加）：
    
    /**
     * 无参构造函数
     * 
     * 创建一个空的按钮对象，后续可以通过 setter 方法设置属性。
     * 主要用于需要动态创建按钮的场景。
     */
    public InlineKeyboardButtonDto() {
        // 字段使用默认值 null
    }
    
    /**
     * 构造函数 - 回调按钮
     * 
     * 创建一个标准回调按钮，点击后会发送回调查询到 Bot。
     * 这是最常用的按钮类型，适用于大多数交互场景。
     * 
     * @param text 按钮显示文本
     * @param callbackData 点击按钮后发送的回调数据
     */
    public InlineKeyboardButtonDto(String text, String callbackData) {
        this.text = text;
        this.callbackData = callbackData;
    }
    
    /**
     * 静态工厂方法 - URL 按钮
     * 
     * 创建一个 URL 按钮，点击后会打开指定的网页。
     * 适用于链接到外部资源或网站的场景。
     * 
     * 注意：当前实现不包含 URL 字段，如果需要 URL 功能，
     * 应该添加 url 字段和相关注解。
     * 
     * @param text 按钮显示文本
     * @param url 点击按钮后打开的网页 URL（当前未使用）
     * @return 按钮实例（当前实际创建的是回调按钮）
     */
    public static InlineKeyboardButtonDto urlButton(String text, String url) {
        // 由于当前类没有 url 字段，我们暂时使用 callbackData 
        // 实际使用时应该添加 url 字段并修改此方法
        return new InlineKeyboardButtonDto(text, "url:" + url);
    }
    
    /**
     * 判断是否为回调按钮
     * 
     * 检查按钮是否配置了回调数据。
     * 
     * @return 如果是回调按钮返回 true，否则返回 false
     */
    public boolean isCallbackButton() {
        return callbackData != null && !callbackData.isEmpty();
    }
    
    /**
     * 解析回调操作
     * 
     * 从回调数据中提取操作部分（第一个冒号之前的部分）。
     * 
     * @return 操作字符串，如果数据为空则返回 null
     */
    public String getAction() {
        if (!isCallbackButton()) {
            return null;
        }
        
        int colonIndex = callbackData.indexOf(':');
        return colonIndex > 0 ? callbackData.substring(0, colonIndex) : callbackData;
    }
}