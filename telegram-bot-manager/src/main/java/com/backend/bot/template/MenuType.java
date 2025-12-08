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
     * 根据 botType 字符串生成 InlineKeyboardMarkup 的工厂方法。
     *
     * @param inputType 机器人类型字符串 (BotEntity.botType) 或回调数据
     * @return InlineKeyboardMarkupDto 对象
     */
    public static InlineKeyboardMarkupDto createDynamicKeyboard(String inputType) {
        if (inputType == null || inputType.isBlank()) {
            return null;
        }

        // 1. 检查输入是初始 BotType 还是回调数据
        boolean isCallback = inputType.startsWith(CALLBACK_PREFIX);

        // 提取用于匹配的关键词
        String keyword = isCallback
                ? inputType.substring(CALLBACK_PREFIX.length())
                : inputType; // 非回调时，keyword 即为原始 botType

        // 统一转为大写进行匹配
        MenuTemplate template = switch (keyword.toUpperCase()) {

            // --- A. 初始 BotType 匹配：返回对应的主菜单 ---
            // 初始 botType 触发时，直接返回该 bot 的主菜单
            case "IP_WHITE_LIST" -> new IpWhitelistMenu();

            case "TOOL" -> new ToolMenu();

            case "CUSTOMER_SERVICE" -> new CustomerServiceMenu();

            // --- B. 回调数据匹配：返回二级菜单或最终操作键盘 ---

            // 注意：这里的 case 需要与你在 Step 1 定义的 **按钮回调数据** 匹配
            case "DOMAIN_WHITELIST_ACTION" -> new IpWhitelistSubMenu();

            // 🌟 新增：最终操作的回调数据，返回 null
            case "FRONTEND_DOMAIN_ACTION", "BACKEND_DOMAIN_ACTION", "MIDDLEWARE_DOMAIN_ACTION" -> {
                yield null; // 明确返回 null，表示不生成新键盘
            }

//            case "ASSET_INFO_ACTION" -> new AssetInfoMenu();
//            case "DEVOP_DUTY_ACTION" -> new DevopDutyMenu();
//
//            case "TOOL_CORE_ACTION" -> new ToolCoreSubMenu();
//            case "TOOL_CONFIG_ACTION" -> new ToolConfigSubMenu();
//
//            case "CS_CONTACT_ACTION" -> new ContactActionMenu();
//            case "CS_FAQ_ACTION" -> new FaqActionMenu();


            default -> {
                log.warn("Unknown keyboard keyword encountered: {}", keyword);
                yield null;
            }
        };

        return template != null ? template.createKeyboard() : null;
    }

    /**
     * 🌟 遗留方法：为了兼容 StartCommandHandler 的旧调用方式，但现在 StartCommandHandler 应该直接调用
     * createDynamicKeyboard("IP_WHITE_LIST") 来获取主菜单。
     *
     * @deprecated 应该使用 createDynamicKeyboard("IP_WHITE_LIST") 替代
     */
    @Deprecated
    public static InlineKeyboardMarkupDto createMainMenu() {
        return createDynamicKeyboard("IP_WHITE_LIST");
    }
}