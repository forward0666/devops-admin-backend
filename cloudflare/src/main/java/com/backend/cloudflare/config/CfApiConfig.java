package com.backend.cloudflare.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "cf")
public class CfApiConfig {
    private String baseUrl = "https://api.cloudflare.com/client/v4";
}