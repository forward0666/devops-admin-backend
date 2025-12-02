package com.backend.bot.template;

import com.backend.bot.dto.InlineKeyboardButtonDto;
import com.backend.bot.dto.InlineKeyboardMarkupDto;

public class CustomerServiceMenu implements MenuTemplate {

    @Override
    public InlineKeyboardMarkupDto createKeyboard() {
        InlineKeyboardMarkupDto markup = new InlineKeyboardMarkupDto();

        markup.addRow(
                new InlineKeyboardButtonDto("📞 1. 联系客服", "callback_data_CS_CONTACT_ACTION")
        );
        markup.addRow(
                new InlineKeyboardButtonDto("📖 2. 常见问题", "callback_data_CS_FAQ_ACTION")
        );
        return markup;
    }
}