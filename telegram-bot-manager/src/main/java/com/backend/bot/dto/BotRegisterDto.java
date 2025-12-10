package com.backend.bot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Bot 注册数据传输对象
 * 
 * 该类用于接收和处理 Telegram Bot 注册请求，包含创建新 Bot 所需的全部信息。
 * 继承自 BaseBotConfigDto 基类，复用了 botName 和 secretToken 字段，
 * 同时添加了 Bot 注册特有的字段。
 * 
 * 注册流程：
 * 1. 验证 Token 有效性
 * 2. 获取 Bot 基本信息
 * 3. 保存到数据库
 * 4. 设置 Webhook
 * 5. 返回注册结果
 * 
 * 安全考虑：
 * - 所有输入都经过验证注解检查
 * - Token 只在注册时验证，不存储明文
 * - 使用 HTTPS 传输敏感信息
 * 
 * @author Backend Team
 * @version 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true) // 继承父类字段并包含在 equals/hashCode 中
public class BotRegisterDto extends BaseBotConfigDto {
    
    /**
     * Telegram Bot 用户名
     * 
     * 在 Telegram 平台上的唯一用户名，格式为 @username。
     * 用于在 Telegram 上识别和访问 Bot。
     * 
     * 验证规则：
     * - 不能为空
     * - 必须以 @ 符号开头（可选，取决于前端处理）
     * - 长度限制（由 Telegram API 决定）
     * 
     * 示例：@my_bot, @customer_service_bot
     */
    @NotBlank(message = "botUsername 不能为空")
    private String botUsername;
    
    /**
     * Telegram Bot Token
     * 
     * 从 BotFather 获取的访问令牌，用于调用 Telegram API。
     * 这是 Bot 的身份凭证，具有完全的 API 访问权限。
     * 
     * 安全考虑：
     * - 应该加密存储在数据库中
     * - 不应在日志中完整显示
     * - 定期轮换以提高安全性
     * - 仅授权人员可访问
     * 
     * 验证规则：不能为空
     * 
     * 获取方式：
     * 1. 与 @BotFather 对话
     * 2. 使用 /newbot 命令
     * 3. 按提示设置 Bot 名称和用户名
     * 4. BotFather 返回 Token
     */
    @NotBlank(message = "Token 不能为空")
    private String token;
    
    // botName 和 secretToken 字段已从 BaseBotConfigDto 继承
}
