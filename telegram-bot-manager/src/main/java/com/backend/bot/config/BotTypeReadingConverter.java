package com.backend.bot.config;

import com.backend.bot.enums.BotType;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

@ReadingConverter // 从数据库 String 到 Java 枚举
public class BotTypeReadingConverter implements Converter<String, BotType> {
    @Override
    public BotType convert(String source) {
        for (BotType type : BotType.values()) {
            if (type.getDbValue().equals(source)) {
                return type;
            }
        }
        // 抛出异常或返回默认值，取决于业务需求
        throw new IllegalArgumentException("Unknown BotType value: " + source);
    }
}
