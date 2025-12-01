package com.backend.bot.config;

import com.backend.bot.enums.BotType;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;

// 在某个配置包下 (e.g., com.backend.bot.config.R2dbcConverterConfig)
@WritingConverter // 从 Java 枚举到数据库 String
public class BotTypeWritingConverter implements Converter<BotType, String> {
    @Override
    public String convert(BotType source) {
        return source.getDbValue();
    }
}

