package com.backend.bot.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Bot 类型枚举类
 * 
 * GENERAL: 通用 Bot，正常响应消息和菜单
 * ALERT: 告警 Bot，暂不响应，后续实现
 */
public enum BotType {
    
    GENERAL("general"),
    
    ALERT("alert");

    private final String dbValue;

    BotType(String dbValue) {
        this.dbValue = dbValue;
    }

    @JsonValue
    public String getDbValue() {
        return dbValue;
    }

    public static BotType fromDbValue(String value) {
        for (BotType type : values()) {
            if (type.dbValue.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid bot type: " + value);
    }
}
