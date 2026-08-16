package com.backend.gateway.config;

import config.ThreadPoolConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(ThreadPoolConfig.class)
public class GatewayImportConfig {
}