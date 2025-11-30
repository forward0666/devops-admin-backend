package com.backend.bot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Telegram Inline Keyboard Markup 顶级对象（DTO）。
 * 用于 Jackson 序列化为发送给 Telegram 的 JSON。
 */
@Getter
@Setter
public class InlineKeyboardMarkupDto {
    @JsonProperty("inline_keyboard")
    private List<List<InlineKeyboardButtonDto>> inlineKeyboard;

    public InlineKeyboardMarkupDto(List<List<InlineKeyboardButtonDto>> keyboard) {
        this.inlineKeyboard = keyboard;
    }
}