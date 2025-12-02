package com.backend.bot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
// import 语句保持不变
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList; // 引入 ArrayList
import java.util.Arrays;
import java.util.List;

/**
 * Telegram Inline Keyboard Markup 顶级对象（DTO）。
 * 用于 Jackson 序列化为发送给 Telegram 的 JSON。
 */
@Getter
@Setter
// 建议添加 @NoArgsConstructor, @AllArgsConstructor (如果使用 Lombok)
public class InlineKeyboardMarkupDto {

    @JsonProperty("inline_keyboard")
    // 初始化为 ArrayList，确保即使没有构造函数，字段也是可变的
    private List<List<InlineKeyboardButtonDto>> inlineKeyboard = new ArrayList<>();

    // ✅ 修复 1: 显式添加一个无参数构造函数（或使用 Lombok 的 @NoArgsConstructor）
    // 如果你在字段上做了初始化 (如上所示: = new ArrayList<>()), 这个构造函数可能不需要额外操作。
    public InlineKeyboardMarkupDto() {
        // 字段已初始化为 ArrayList，这里留空即可
    }

    // ✅ 保持旧的带参数构造函数，但确保它复制列表以避免不可变性问题
    public InlineKeyboardMarkupDto(List<List<InlineKeyboardButtonDto>> keyboard) {
        // 使用 ArrayList 包装传入的列表，确保内部列表是可变的
        this.inlineKeyboard = new ArrayList<>(keyboard);
    }

    // ✅ addRow 方法：现在 this.inlineKeyboard 保证是 ArrayList，不会抛出 UOE
    public void addRow(InlineKeyboardButtonDto... buttons) {
        if (buttons != null && buttons.length > 0) {
            // Arrays.asList 返回的 List 是固定大小的，但可以被添加到 ArrayList 中
            this.inlineKeyboard.add(Arrays.asList(buttons));
        }
    }
}