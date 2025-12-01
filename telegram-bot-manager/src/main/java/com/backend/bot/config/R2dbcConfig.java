package com.backend.bot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.dialect.MySqlDialect; // 假设您使用 MySQL

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableR2dbcAuditing // 如果您使用了 R2DBC 审计
public class R2dbcConfig {

    @Bean
    public R2dbcCustomConversions r2dbcCustomConversions() {
        List<Converter<?, ?>> converters = new ArrayList<>();

        // 🚀 注册自定义的读写转换器
        converters.add(new BotTypeReadingConverter());
        converters.add(new BotTypeWritingConverter());

        // 根据您使用的数据库方言进行创建
        return R2dbcCustomConversions.of(MySqlDialect.INSTANCE, converters);
    }
}