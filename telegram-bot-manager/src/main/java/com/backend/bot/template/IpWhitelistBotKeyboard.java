package com.backend.bot.template;

import com.backend.bot.dto.InlineKeyboardButtonDto; // <--- 导入 DTO
import com.backend.bot.dto.InlineKeyboardMarkupDto; // <--- 导入 DTO
import java.util.List;

public class IpWhitelistBotKeyboard implements KeyboardTemplate {

    @Override
    public InlineKeyboardMarkupDto createKeyboard() { // <--- 返回类型更改
        List<InlineKeyboardButtonDto> row1 = List.of( // <--- 按钮类型更改
                new InlineKeyboardButtonDto("➕ 添加 IP", "ACTION_ADD_IP"),
                new InlineKeyboardButtonDto("➖ 移除 IP", "ACTION_REMOVE_IP")
        );
        List<InlineKeyboardButtonDto> row2 = List.of(
                new InlineKeyboardButtonDto("📋 查看白名单", "ACTION_VIEW_LIST")
        );
        List<InlineKeyboardButtonDto> row3 = List.of(
                new InlineKeyboardButtonDto("❓ 帮助", "ACTION_HELP_IP")
        );

        return new InlineKeyboardMarkupDto(List.of(row1, row2, row3)); // <--- 构造函数更改
    }
}