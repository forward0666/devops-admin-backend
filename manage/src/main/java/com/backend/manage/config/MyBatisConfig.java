package com.backend.manage.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis配置类 - 使用Spring Boot自动配置简化配置
 * 
 * 中文注释：这个配置类负责配置MyBatis框架在Spring Boot应用中的集成
 * 使用@MapperScan注解自动扫描指定包下的Mapper接口，无需手动配置SqlSessionFactory
 * Spring Boot会根据application.properties中的配置自动完成MyBatis的配置
 */
@Configuration
@MapperScan("com.backend.manage.mapper")
public class MyBatisConfig {
    // Spring Boot会自动根据application.properties中的配置来配置MyBatis
    // 无需手动配置SqlSessionFactory，简化了配置过程
}
