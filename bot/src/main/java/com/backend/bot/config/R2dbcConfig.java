package com.backend.bot.config;

import com.backend.bot.enums.BotType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.dialect.Dialect;
import org.springframework.data.r2dbc.dialect.MySqlDialect;
import org.springframework.data.convert.CustomConversions;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Configuration
@EnableR2dbcAuditing
public class R2dbcConfig {

    @Bean
    @ReadingConverter
    public Converter<String, BotType> botTypeReadingConverter() {
        return new Converter<String, BotType>() {
            @Override
            public BotType convert(String source) {
                for (BotType type : BotType.values()) {
                    if (type.getDbValue().equals(source)) {
                        return type;
                    }
                }
                throw new IllegalArgumentException("Unknown BotType value: " + source);
            }
        };
    }

    @Bean
    @WritingConverter
    public Converter<BotType, String> botTypeWritingConverter() {
        return new Converter<BotType, String>() {
            @Override
            public String convert(BotType source) {
                return source.getDbValue();
            }
        };
    }

    @Bean
    public R2dbcCustomConversions r2dbcCustomConversions(
            Converter<String, BotType> botTypeReadingConverter,
            Converter<BotType, String> botTypeWritingConverter) {
        List<Converter<?, ?>> converters = new ArrayList<>();
        converters.add(botTypeReadingConverter);
        converters.add(botTypeWritingConverter);
        return new R2dbcCustomConversions(Dialect.getDefault(), converters);
    }
}
