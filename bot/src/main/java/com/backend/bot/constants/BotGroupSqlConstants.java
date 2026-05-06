package com.backend.bot.constants;

public class BotGroupSqlConstants {

    public static final String FIND_TOPICS_BY_BOT_NAME_AND_CHAT_ID_ORDER_BY_SORT =
            "SELECT * FROM bot_group_topic WHERE bot_name = :botName AND chat_id = :chatId ORDER BY sort_order ASC, id ASC";
}
