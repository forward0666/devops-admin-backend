package com.backend.bot.enums;

public enum BotType {
    IP_WHITE_LIST("ip_white_list"),
    ADMIN("admin"),
    SYSTEM("system");

    private final String dbValue;

    BotType(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }
}