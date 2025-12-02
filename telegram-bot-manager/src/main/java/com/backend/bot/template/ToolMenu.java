package com.backend.bot.template;

import com.backend.bot.dto.InlineKeyboardButtonDto;
import com.backend.bot.dto.InlineKeyboardMarkupDto;

public class ToolMenu implements MenuTemplate {

    @Override
    public InlineKeyboardMarkupDto createKeyboard() {
        InlineKeyboardMarkupDto markup = new InlineKeyboardMarkupDto();

        markup.addRow(
                new InlineKeyboardButtonDto("🛠️ 1. 核心工具箱", "callback_data_TOOL_CORE_ACTION")
        );
        markup.addRow(
                new InlineKeyboardButtonDto("⚙️ 2. 系统配置", "callback_data_TOOL_CONFIG_ACTION")
        );
        return markup;
    }
}