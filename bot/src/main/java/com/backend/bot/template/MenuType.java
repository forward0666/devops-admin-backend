package com.backend.bot.template;

import com.backend.bot.dto.InlineKeyboardMarkupDto;
import lombok.extern.slf4j.Slf4j;

/**
 * Telegram 键盘模板的工具类。
 * 提供兜底键盘创建逻辑，当数据库菜单不可用时使用。
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
     * 硬编码兜底：菜单已迁移到数据库存储，此处返回 null 由调用方处理。
     */
    public static InlineKeyboardMarkupDto createFallbackKeyboard(String inputType) {
        if (inputType == null || inputType.isBlank()) {
            return null;
        }

        boolean isCallback = inputType.startsWith(CALLBACK_PREFIX);
        String keyword = isCallback
                ? inputType.substring(CALLBACK_PREFIX.length())
                : inputType;

        log.warn("No fallback keyboard for keyword: {} (menus are now database-driven)", keyword);
        return null;
    }
}
