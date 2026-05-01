package com.backend.bot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Telegram API 配置属性类
 * 
 * 该类负责管理 Telegram Bot API 相关的配置参数，
 * 使用 Spring Boot 的 ConfigurationProperties 机制，
 * 从 application.properties 或 Nacos 配置中心读取配置。
 * 
 * 配置项：
 * - apiBaseUrl: Telegram API 基础 URL
 * - webhookDomain: Webhook 域名配置，用于接收 Telegram 更新
 * 
 * @author Backend Team
 * @version 1.0.0
 */
@Data                                       // Lombok 注解，自动生成 getter/setter、toString 等方法
@Configuration                               // Spring 配置类注解，表明这是一个配置类
@ConfigurationProperties(prefix = "telegram") // 绑定前缀为 "telegram" 的配置属性
public class TelegramProperties {
    
    /**
     * Telegram API 基础 URL
     * 
     * 默认值为 Telegram 官方 API 地址：https://api.telegram.org
     * 在某些特殊环境下（如使用代理或私有部署），可能需要修改此配置
     */
    private String apiBaseUrl = "https://api.telegram.org";
    
    /**
     * Webhook 域名配置
     * 
     * 用于设置 Telegram Bot Webhook 的回调 URL 域名部分。
     * 完整的 Webhook URL 格式为：{webhookDomain}/bot/{token}/webhook
     * 
     * 注意：
     * - 必须是公网可访问的 HTTPS 地址
     * - 可以是 IP 地址或域名
     * - 需要在应用启动前配置好
     */
    private String webhookDomain;

    private String gatewayBotSecret;
}
