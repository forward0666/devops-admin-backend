package com.backend.bot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "telegram")
public class TelegramProperties {
    private String apiBaseUrl = "https://api.telegram.org";
    private String webhookDomain;
}
