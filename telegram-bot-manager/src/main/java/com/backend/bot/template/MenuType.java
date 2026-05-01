package com.backend.bot.template;

import com.backend.bot.dto.InlineKeyboardMarkupDto;
import lombok.extern.slf4j.Slf4j;

/**
 * Telegram 键盘模板的工厂类。
 * 负责根据 BotType 查找并实例化正确的模板。
 */
@Slf4j
public class MenuType {

    // 主菜单回调数据的前缀
    private static final String CALLBACK_PREFIX = "callback_data_";

    /**
     * 静态方法：尝试从数据库加载菜单，查不到再用硬编码兜底。
     * 仅用于非 Spring 管理的场景或兼容旧调用。
     *
     * @deprecated 推荐使用 BotMenuService 的响应式方法
     */
    public static InlineKeyboardMarkupDto createDynamicKeyboard(String inputType) {
        // 纯静态方法无法注入 BotMenuService，直接使用硬编码兜底
        return createFallbackKeyboard(inputType);
    }

    /**
     * 硬编码兜底：保留原有逻辑
     */
    public static InlineKeyboardMarkupDto createFallbackKeyboard(String inputType) {
        if (inputType == null || inputType.isBlank()) {
            return null;
        }

        boolean isCallback = inputType.startsWith(CALLBACK_PREFIX);
        String keyword = isCallback
                ? inputType.substring(CALLBACK_PREFIX.length())
                : inputType;

        MenuTemplate template = switch (keyword.toUpperCase()) {
            case "IP_WHITE_LIST" -> new IpWhitelistMenu();
            case "DOMAIN_WHITELIST_ACTION" -> new IpWhitelistSubMenu();
            case "FRONTEND_WEB_DOMAIN_ACTION", "FRONTEND_ADMIN_DOMAIN_ACTION" -> null;
            default -> {
                log.warn("Unknown keyboard keyword encountered: {}", keyword);
                yield null;
            }
        };

        return template != null ? template.createKeyboard() : null;
    }

    /**
     * @deprecated 应该使用 createDynamicKeyboard("IP_WHITE_LIST") 替代
     */
    @Deprecated
    public static InlineKeyboardMarkupDto createMainMenu() {
        return createDynamicKeyboard("IP_WHITE_LIST");
    }
}
