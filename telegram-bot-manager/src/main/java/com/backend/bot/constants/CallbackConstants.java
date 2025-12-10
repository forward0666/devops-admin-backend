package com.backend.bot.constants;

/**
 * 集中管理所有 Telegram 回调数据和用户会话状态常量。
 * 这样做可以避免在多个处理器类中硬编码字符串，提高可维护性。
 */
public class CallbackConstants {
    // --- 菜单导航常量 ---
    /** 返回主菜单的回调 */
    public static final String MAIN_MENU_BACK = "MAIN_MENU_BACK";
    /** 主菜单的根回调（用于 MenuType 判断） */
    public static final String MAIN_MENU_CALLBACK = "MAIN_MENU";

    // --- 最终业务操作常量：域名加白 ---
    public static final String FRONTEND_WEB_ACTION = "callback_data_FRONTEND_WEB_DOMAIN_ACTION";
    public static final String FRONTEND_ADMIN_ACTION = "callback_data_FRONTEND_ADMIN_DOMAIN_ACTION";
//    public static final String MIDDLEWARE_ACTION = "callback_data_MIDDLEWARE_DOMAIN_ACTION";

    // --- 用户会话状态常量 (用于 TextUpdateHandler) ---
    /** 等待前台域名 IP 输入 */
    public static final String STATE_AWAITING_FRONTEND_WEB_IP = "AWAITING_FRONTEND_WEB_IP";
    /** 等待后台域名 IP 输入 */
    public static final String STATE_AWAITING_FRONTEND_ADMIN_IP = "AWAITING_FRONTEND_ADMIN_IP";
//    public static final String STATE_AWAITING_MIDDLEWARE_IP = "AWAITING_MIDDLEWARE_IP";
}