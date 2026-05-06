package com.backend.bot.constants;

public class BotConfigSqlConstants {

    public static final String UPDATE_WEBHOOK_URL =
            "UPDATE bot_config SET webhook_url = :webhookUrl WHERE bot_name = :botName";

    public static final String CLEAR_WEBHOOK_URL =
            "UPDATE bot_config SET webhook_url = NULL WHERE bot_name = :botName";
}
