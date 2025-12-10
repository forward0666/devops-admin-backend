package com.backend.bot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Bot Webhook 配置数据传输对象
 * 
 * 该类用于接收和处理 Telegram Bot Webhook 设置请求，包含配置 Webhook 所需的全部信息。
 * 继承自 BaseBotConfigDto 基类，复用了 botName 和 secretToken 字段，
 * 同时添加了 Webhook 配置特有的字段。
 * 
 * Webhook 机制：
 * - Telegram 将更新推送到我们配置的 URL
 * - 无需轮询 Telegram API，提高效率
 * - 支持实时响应和低延迟处理
 * 
 * 配置流程：
 * 1. 验证 URL 可访问性
 * 2. 验证 Secret Token 安全性
 * 3. 调用 Telegram API 设置 Webhook
 * 4. 保存配置到数据库
 * 5. 返回配置结果
 * 
 * 安全考虑：
 * - URL 必须是 HTTPS
 * - Secret Token 用于验证请求来源
 * - 定期更新 Secret Token 提高安全性
 * 
 * @author Backend Team
 * @version 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true) // 继承父类字段并包含在 equals/hashCode 中
public class SetWebhookDto extends BaseBotConfigDto {
    
    /**
     * Webhook 回调 URL
     * 
     * Telegram 将 Bot 更新事件发送到此 URL。
     * 这必须是公网可访问的 HTTPS 地址，Telegram 不支持 HTTP 回调。
     * 
     * URL 格式：https://your-domain.com/api/bot/{botToken}/webhook
     * 
     * 验证规则：
     * - 不能为空
     * - 必须是有效的 URL 格式
     * - 必须使用 HTTPS 协议
     * - 必须是公网可访问的
     * 
     * 安全考虑：
     * - 使用 HTTPS 确保传输加密
     * - 定期更新证书
     * - 使用 Secret Token 验证请求
     * - 考虑使用 CDN 或防火墙保护
     */
    @NotBlank(message = "URL is required")
    private String url;
    
    // botName 和 secretToken 字段已从 BaseBotConfigDto 继承
}