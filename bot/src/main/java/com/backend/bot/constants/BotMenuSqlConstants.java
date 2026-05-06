package com.backend.bot.constants;

public class BotMenuSqlConstants {

    public static final String FIND_BY_BOT_NAME =
            "SELECT * FROM bot_menu WHERE bot_name = :botName";

    public static final String FIND_BY_BOT_NAME_ORDERED =
            "SELECT * FROM bot_menu WHERE bot_name = :botName ORDER BY menu_level, sort_order";

    public static final String FIND_BY_BOT_NAME_AND_MENU_KEY =
            "SELECT * FROM bot_menu WHERE bot_name = :botName AND menu_key = :menuKey";

    public static final String FIND_BY_BOT_NAME_AND_MENU_LEVEL_ORDERED =
            "SELECT * FROM bot_menu WHERE bot_name = :botName AND menu_level = :menuLevel ORDER BY sort_order";

    public static final String FIND_BY_BOT_TYPE_AND_MENU_LEVEL =
            "SELECT * FROM bot_menu WHERE bot_type = :botType AND menu_level = :menuLevel AND bot_name IS NULL ORDER BY sort_order LIMIT 1";
}
