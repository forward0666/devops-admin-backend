package com.backend.bot.template;
import com.backend.bot.dto.InlineKeyboardButtonDto;
import com.backend.bot.dto.InlineKeyboardMarkupDto;

public class IpWhitelistSubMenu implements MenuTemplate{
    @Override
    public InlineKeyboardMarkupDto createKeyboard() {
        InlineKeyboardMarkupDto inlineKeyboard = new InlineKeyboardMarkupDto();
        String navigate = "\u00A0\u00A0\u00A0\u00A0\u00A0\u00A0" + "👉👉";
        inlineKeyboard.addRow(
                // 增加 🌐 图标
                new InlineKeyboardButtonDto("1、前台域名" + navigate, "callback_data_FRONTEND_DOMAIN_ACTION") // 建议修改回调数据，避免与上级菜单冲突
        );
        inlineKeyboard.addRow(
                // 增加 🏢 图标
                new InlineKeyboardButtonDto("2、后台域名" + navigate, "callback_data_BACKEND_DOMAIN_ACTION") // 建议修改回调数据
        );

        inlineKeyboard.addRow(
                // 增加 🧑‍💻 图标
                new InlineKeyboardButtonDto("3、中间件域名" + navigate, "callback_data_MIDDLEWARE_DOMAIN_ACTION") // 建议修改回调数据
        );

        // 分隔线
        inlineKeyboard.addRow();

        // 🌟 关键：添加返回按钮
        inlineKeyboard.addRow(
                // 回调数据设置为上一级菜单的关键词，让 MenuType 知道返回 IpWhitelistMenu
                new InlineKeyboardButtonDto("🔙 返回主菜单", "callback_data_IP_WHITE_LIST")
        );

        return inlineKeyboard;
    }
}