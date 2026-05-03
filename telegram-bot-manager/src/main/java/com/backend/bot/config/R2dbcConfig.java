package com.backend.bot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;

import java.util.ArrayList;
import java.util.List;

/**
 * R2DBC 数据库配置类
 * 
 * 该类负责配置响应式数据库连接相关的组件，包括：
 * 1. 自定义类型转换器 - 处理数据库与 Java 对象之间的类型转换
 * 2. 数据库方言配置 - 针对不同数据库的特定行为
 * 3. 审计功能支持 - 自动处理创建时间、更新时间等审计字段
 * 
 * R2DBC (Reactive Relational Database Connectivity) 是响应式编程范式下的
 * 数据库访问规范，与传统 JDBC 不同，它基于 Project Reactor 提供非阻塞 IO。
 * 
 * @author Backend Team
 * @version 1.0.0
 */
@Configuration                              // Spring 配置类注解，定义配置类
@EnableR2dbcAuditing                        // 启用 R2DBC 审计功能，自动处理 @CreatedDate、@LastModifiedDate 等注解
public class R2dbcConfig {

    /**
     * 配置 R2DBC 自定义类型转换器 Bean
     * 
     * 该 Bean 用于注册自定义的数据库类型转换器，处理特殊数据类型在
     * 数据库存储和 Java 对象之间的转换过程。
     * 
     * 主要功能：
     * 1. 注册枚举类型转换器 (BotType)
     * 2. 支持数据库字段与 Java 枚举的双向转换
     * 3. 集成 MySQL 数据库方言
     * 
     * @return R2dbcCustomConversions 自定义转换器集合
     */
    @Bean
    public R2dbcCustomConversions r2dbcCustomConversions() {
        List<Converter<?, ?>> converters = new ArrayList<>();

        // 🚀 注册 BotType 枚举类型的读写转换器
        // BotTypeReadingConverter: 从数据库读取字符串并转换为 BotType 枚举
        converters.add(new BotTypeReadingConverter());
        // BotTypeWritingConverter: 将 BotType 枚举转换为字符串存入数据库
        converters.add(new BotTypeWritingConverter());

        // 使用 MySQL 数据库方言创建自定义转换器集合
        // 数据库方言负责处理不同数据库的特定 SQL 语法和数据类型
        return R2dbcCustomConversions.of(MySqlDialect.INSTANCE, converters);
    }
}