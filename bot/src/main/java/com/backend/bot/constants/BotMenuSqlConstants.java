package com.backend.bot.constants;

public class BotMenuSqlConstants {

    public static final String FIND_BY_BOT_TYPE_ORDERED =
            "SELECT * FROM bot_menu WHERE bot_type = :botType ORDER BY menu_level, sort_order";

    public static final String FIND_BY_BOT_TYPE_AND_MENU_KEY =
            "SELECT * FROM bot_menu WHERE bot_type = :botType AND menu_key = :menuKey";

    public static final String FIND_BY_BOT_TYPE_AND_MENU_LEVEL =
            "SELECT * FROM bot_menu WHERE bot_type = :botType AND menu_level = :menuLevel ORDER BY sort_order";
}
