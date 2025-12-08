package com.backend.bot.template;

import com.backend.bot.dto.InlineKeyboardButtonDto;
import com.backend.bot.dto.InlineKeyboardMarkupDto;

public class IpWhitelistMenu implements MenuTemplate {

    @Override
    public InlineKeyboardMarkupDto createKeyboard() {
        InlineKeyboardMarkupDto inlineKeyboard = new InlineKeyboardMarkupDto();
        String navigate = "\u00A0\u00A0\u00A0\u00A0\u00A0\u00A0" + "👉👉";
        inlineKeyboard.addRow(
                // 增加 🌐 图标
                new InlineKeyboardButtonDto("1、域名加白" + navigate, "callback_data_DOMAIN_WHITELIST_ACTION")
        );
        inlineKeyboard.addRow(
                // 增加 🏢 图标
                new InlineKeyboardButtonDto("2、资产信息" + navigate, "callback_data_ASSET_INFO_ACTION")
        );

        inlineKeyboard.addRow(
                // 增加 🧑‍💻 图标
                new InlineKeyboardButtonDto("3、值班运维" + navigate, "callback_data_DEVOP_DUTY_ACTION")
        );

        inlineKeyboard.addRow(
                // 增加 🧑‍💻 图标
                new InlineKeyboardButtonDto("4、其他" + "\u00A0\u00A0\u00A0\u00A0\u00A0\u00A0\u00A0" + navigate, "callback_data_OTHER_ACTION")
        );
        return inlineKeyboard;
    }
}