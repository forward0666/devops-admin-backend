package com.backend.bot.template;

import com.backend.bot.dto.InlineKeyboardButtonDto;
import com.backend.bot.dto.InlineKeyboardMarkupDto;
import java.util.List;

public class ToolBotKeyboard implements KeyboardTemplate {

    @Override
    public InlineKeyboardMarkupDto createKeyboard() {
        List<InlineKeyboardButtonDto> row1 = List.of(
                new InlineKeyboardButtonDto("⏱️ 计时工具", "ACTION_TIMER"),
                new InlineKeyboardButtonDto("📊 数据查询", "ACTION_QUERY_DATA")
        );
        List<InlineKeyboardButtonDto> row2 = List.of(
                new InlineKeyboardButtonDto("⚙️ 设置", "ACTION_SETTINGS")
        );

        return new InlineKeyboardMarkupDto(List.of(row1, row2));
    }
}