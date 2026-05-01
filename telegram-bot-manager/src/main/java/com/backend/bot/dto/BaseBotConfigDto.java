package com.backend.bot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Bot 基础配置数据传输对象
 * 
 * 该抽象类提供了 Bot 配置相关的公共字段，作为其他 Bot 配置 DTO 的基类。
 * 通过提取公共字段，遵循 DRY（Don't Repeat Yourself）原则，
 * 减少代码重复，提高可维护性。
 * 
 * 公共字段：
 * - botName: Bot 显示名称
 * - secretToken: 安全令牌，用于验证 Webhook 请求
 * 
 * 设计特点：
 * 1. 抽象类设计，不能直接实例化
 * 2. 包含验证注解，确保数据有效性
 * 3. 使用 Lombok 简化代码
 * 4. 为子类提供统一的基础功能
 * 
 * 子类：
 * - BotRegisterDto: 用于 Bot 注册
 * - SetWebhookDto: 用于 Webhook 配置
 * 
 * @author Backend Team
 * @version 1.0.0
 */
@Data
public abstract class BaseBotConfigDto {
    
    /**
     * Bot 显示名称
     * 
     * 用户自定义的 Bot 名称，用于在管理界面和日志中识别。
     * 这不是 Telegram 的用户名（@username），而是内部管理系统中的名称。
     * 
     * 验证规则：不能为空
     * 
     * 示例：客服机器人、IP白名单管理器、运维工具Bot
     */
    @NotBlank(message = "Bot name is required")
    private String botName;
    
    /**
     * 安全令牌
     * 
     * 用于验证 Telegram Webhook 请求的安全令牌。
     * 当 Telegram 发送更新到我们的 Webhook 端点时，
     * 会在请求头中包含此令牌，我们可以用它来验证请求的真实性。
     * 
     * 安全考虑：
     * - 应该使用随机生成的强令牌
     * - 定期更换令牌提高安全性
     * - 不应在日志中暴露完整令牌
     * 
     * 验证规则：不能为空
     * 
     * 生成建议：使用 UUID 或加密库生成的随机字符串
     */
    private String secretToken;
}