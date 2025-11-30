package com.backend.bot.template;

import com.backend.bot.dto.InlineKeyboardButtonDto; // <--- 修复导入
import com.backend.bot.dto.InlineKeyboardMarkupDto; // <--- 修复导入
import java.util.List;

// 注意：这里的类名不再是内部类，所以不再需要 TelegramMarkup.前缀
public class CustomerServiceKeyboard implements KeyboardTemplate {

    @Override
    public InlineKeyboardMarkupDto createKeyboard() { // <--- 修复返回类型
        List<InlineKeyboardButtonDto> row1 = List.of( // <--- 修复使用类型
                new InlineKeyboardButtonDto("👤 联系客服", "ACTION_CONTACT_SUPPORT"),
                new InlineKeyboardButtonDto("📖 常见问题", "ACTION_FAQ")
        );
        List<InlineKeyboardButtonDto> row2 = List.of( // <--- 修复使用类型
                new InlineKeyboardButtonDto("📢 最新通知", "ACTION_ANNOUNCEMENT")
        );

        return new InlineKeyboardMarkupDto(List.of(row1, row2)); // <--- 修复构造函数
    }
}