package com.backend.bot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class BotConfig {

    // 从 application.yml/properties 获取 Telegram API 的基础 URL
    // 假设您在 BotClientService 中设置了默认值，这里使用相同的 key
    @Value("${telegram.api-base-url:https://api.telegram.org}")
    private String telegramApiBaseUrl;

    /**
     * 暴露一个名为 'telegramWebClient' 的 WebClient Bean
     */
    @Bean
    public WebClient telegramWebClient(WebClient.Builder webClientBuilder) {
        return webClientBuilder
                // 确保 WebClient 配置了正确的 Base URL
                .baseUrl(telegramApiBaseUrl)
                .build();
    }
}