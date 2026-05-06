package com.backend.bot.config;

import com.backend.bot.enums.BotType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.dialect.MySqlDialect;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableR2dbcAuditing
public class R2dbcConfig {

    @Bean
    public Converter<String, BotType> botTypeReadingConverter() {
        return source -> {
            for (BotType type : BotType.values()) {
                if (type.getDbValue().equals(source)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown BotType value: " + source);
        };
    }

    @Bean
    public Converter<BotType, String> botTypeWritingConverter() {
        return BotType::getDbValue;
    }

    @Bean
    public R2dbcCustomConversions r2dbcCustomConversions(
            Converter<String, BotType> readingConverter,
            Converter<BotType, String> writingConverter) {
        List<Converter<?, ?>> converters = new ArrayList<>();
        converters.add(readingConverter);
        converters.add(writingConverter);
        return R2dbcCustomConversions.of(MySqlDialect.INSTANCE, converters);
    }
}
