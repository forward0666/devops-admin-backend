package com.backend.bot.constants;

public final class BotMenuSqlConstants {

    private BotMenuSqlConstants() {}

    public static final String FIND_BY_BOT_NAME_ORDER_BY_LEVEL_AND_SORT =
            "SELECT * FROM bot_menu WHERE bot_name = :botName ORDER BY menu_level, sort_order";

    public static final String FIND_BY_BOT_NAME_AND_MENU_LEVEL =
            "SELECT * FROM bot_menu WHERE bot_name = :botName AND menu_level = :menuLevel ORDER BY sort_order";
}
