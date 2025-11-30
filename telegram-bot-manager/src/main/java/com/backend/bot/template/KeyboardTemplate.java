package com.backend.bot.template;

import com.backend.bot.dto.InlineKeyboardMarkupDto; // <--- 修复导入

/**
 * 键盘模板生成接口。
 */
public interface KeyboardTemplate {
    /**
     * 生成特定机器人的内联键盘标记。
     * @return InlineKeyboardMarkupDto 对象。
     */
    InlineKeyboardMarkupDto createKeyboard();
}