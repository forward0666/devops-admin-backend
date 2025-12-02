package com.backend.bot.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum BotType {
    IP_WHITE_LIST("ip_white_list"),
    CUSTOMER_SERVICE("customer_service"),
    TOOL("tool");

    private final String dbValue;

    BotType(String dbValue) {
        this.dbValue = dbValue;
    }

    // 🚀 核心修复：添加 @JsonValue
    // 告诉 Jackson (Redis使用的) 在序列化和反序列化时使用这个方法的返回值。
    @JsonValue
    public String getDbValue() {
        return dbValue;
    }

}