package com.backend.bot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * Telegram Inline Keyboard 按钮对象（DTO）。
 */
@Getter
@Setter
public class InlineKeyboardButtonDto {
    private String text;
    @JsonProperty("callback_data")
    private String callbackData;
    // URL 或 switch_inline_query 等其他字段如果需要也可以添加

    public InlineKeyboardButtonDto(String text, String callbackData) {
        this.text = text;
        this.callbackData = callbackData;
    }
}