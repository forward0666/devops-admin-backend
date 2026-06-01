package com.backend.bot.constants;

public class BotConfigSqlConstants {

    public static final String UPDATE_WEBHOOK_URL =
            "UPDATE bot_config SET webhook_url = :webhookUrl WHERE bot_name = :botName";

    public static final String CLEAR_WEBHOOK_URL =
            "UPDATE bot_config SET webhook_url = NULL WHERE bot_name = :botName";

    public static final String SELECT_BY_BOT_NAME_LIMIT_ONE =
            "SELECT id, bot_name, bot_username, bot_type, bot_token, status, webhook_url, created_at, updated_at FROM bot_config WHERE bot_name = :botName";

    public static final String DELETE_BY_BOT_NAME =
            "DELETE FROM bot_config WHERE bot_name = :botName";

    public static final String EXISTS_BY_BOT_NAME =
            "SELECT COUNT(*) FROM bot_config WHERE bot_name = :botName";

    public static final String EXISTS_BY_BOT_USERNAME =
            "SELECT COUNT(*) FROM bot_config WHERE bot_username = :botUsername";
}
