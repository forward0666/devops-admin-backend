package com.backend.bot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 封装 Telegram Inline Keyboard Markup 的数据结构和生成逻辑。
 */
@Slf4j
public class TelegramMarkup {

    /**
     * Telegram Inline Keyboard Markup 顶级对象。
     */
    @Getter
    @Setter
    public static class InlineKeyboardMarkup {
        @JsonProperty("inline_keyboard")
        private List<List<InlineKeyboardButton>> inlineKeyboard;

        public InlineKeyboardMarkup(List<List<InlineKeyboardButton>> keyboard) {
            this.inlineKeyboard = keyboard;
        }
    }

    /**
     * Telegram Inline Keyboard 按钮对象。
     */
    @Getter
    @Setter
    public static class InlineKeyboardButton {
        private String text;
        @JsonProperty("callback_data")
        private String callbackData;

        public InlineKeyboardButton(String text, String callbackData) {
            this.text = text;
            this.callbackData = callbackData;
        }
    }

    /**
     * 根据 botType 字符串生成 InlineKeyboardMarkup。
     * 假设 botType 字符串为 "CUSTOMER_SERVICE", "TOOL_BOT", "IP_WHITE_LIST" 等。
     *
     * @param botType 机器人类型字符串 (BotEntity.botType)
     * @return InlineKeyboardMarkup 对象
     */
    public static InlineKeyboardMarkup createDynamicKeyboard(String botType) {
        if (botType == null || botType.isBlank()) {
            return null;
        }

        // 统一转为大写进行匹配，以兼容 'ip_white_list', 'Ip_White_List' 等格式
        return switch (botType.toUpperCase()) {
            case "CUSTOMER_SERVICE" -> createCustomerServiceKeyboard();
            case "TOOL_BOT" -> createToolBotKeyboard();
            // ❗ 新增 IP 白名单机器人类型支持
            case "IP_WHITE_LIST" -> createIpWhitelistBotKeyboard();
            default -> {
                log.warn("Unknown botType encountered: {}", botType);
                yield null;
            }
        };
    }

    private static InlineKeyboardMarkup createCustomerServiceKeyboard() {
        List<InlineKeyboardButton> row1 = List.of(
                new InlineKeyboardButton("👤 联系客服", "ACTION_CONTACT_SUPPORT"),
                new InlineKeyboardButton("📖 常见问题", "ACTION_FAQ")
        );
        List<InlineKeyboardButton> row2 = List.of(
                new InlineKeyboardButton("📢 最新通知", "ACTION_ANNOUNCEMENT")
        );

        return new InlineKeyboardMarkup(List.of(row1, row2));
    }

    private static InlineKeyboardMarkup createToolBotKeyboard() {
        List<InlineKeyboardButton> row1 = List.of(
                new InlineKeyboardButton("⏱️ 计时工具", "ACTION_TIMER"),
                new InlineKeyboardButton("📊 数据查询", "ACTION_QUERY_DATA")
        );
        List<InlineKeyboardButton> row2 = List.of(
                new InlineKeyboardButton("⚙️ 设置", "ACTION_SETTINGS")
        );

        return new InlineKeyboardMarkup(List.of(row1, row2));
    }

    /**
     * IP 白名单机器人专用键盘模板。
     */
    private static InlineKeyboardMarkup createIpWhitelistBotKeyboard() {
        List<InlineKeyboardButton> row1 = List.of(
                new InlineKeyboardButton("➕ 添加 IP", "ACTION_ADD_IP"),
                new InlineKeyboardButton("➖ 移除 IP", "ACTION_REMOVE_IP")
        );
        List<InlineKeyboardButton> row2 = List.of(
                new InlineKeyboardButton("📋 查看白名单", "ACTION_VIEW_LIST")
        );
        List<InlineKeyboardButton> row3 = List.of(
                new InlineKeyboardButton("❓ 帮助", "ACTION_HELP_IP")
        );

        return new InlineKeyboardMarkup(List.of(row1, row2, row3));
    }
}