package com.backend.bot.template;

import com.backend.bot.dto.InlineKeyboardMarkupDto; // <--- 导入 DTO
import lombok.extern.slf4j.Slf4j;

/**
 * Telegram 键盘模板的工厂类。
 * 负责根据 BotType 查找并实例化正确的模板。
 */
@Slf4j
public class TelegramMarkup {

    // 移除 InlineKeyboardMarkup 和 InlineKeyboardButton 内部类

    /**
     * 根据 botType 字符串生成 InlineKeyboardMarkup 的工厂方法。
     *
     * @param botType 机器人类型字符串 (BotEntity.botType)
     * @return InlineKeyboardMarkupDto 对象
     */
    public static InlineKeyboardMarkupDto createDynamicKeyboard(String botType) {
        if (botType == null || botType.isBlank()) {
            return null;
        }

        // 统一转为大写进行匹配
        KeyboardTemplate template = switch (botType.toUpperCase()) {
            case "CUSTOMER_SERVICE" -> new CustomerServiceKeyboard();
            case "TOOL_BOT" -> new ToolBotKeyboard();
            case "IP_WHITE_LIST" -> new IpWhitelistBotKeyboard();
            default -> {
                log.warn("Unknown botType encountered: {}", botType);
                yield null;
            }
        };

        return template != null ? template.createKeyboard() : null;
    }
}