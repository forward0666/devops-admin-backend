package com.backend.login.config;

import org.springframework.context.annotation.Configuration;

/**
 * 数据源配置类 - 为管理服务配置数据源
 * 
 * 中文注释：这个配置类使用Spring Boot的自动配置功能来配置数据源
 * 通过application.properties中的配置，Spring Boot会自动配置DataSource和HikariCP连接池
 * 这种配置方式避免了多数据源配置可能产生的冲突问题
 */
@Configuration
public class DataSourceConfig {
    // Spring Boot会根据application.properties中的配置自动配置DataSource和HikariCP连接池
    // 这种配置方式消除了多数据源配置可能产生的冲突
}
