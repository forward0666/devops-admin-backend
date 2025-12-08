package com.backend.bot.util; // 假设您使用 .util 包

import com.backend.bot.dto.BotUpdateDto;
import java.util.Optional;

/**
 * 机器人更新（BotUpdateDto）的辅助工具类。
 * 负责从复杂的更新结构中安全地提取核心信息。
 */
public class BotUpdateUtils {

    // 私有构造函数，防止实例化工具类
    private BotUpdateUtils() {}

    /**
     * 尝试从 BotUpdateDto 中提取 chatId。
     * 支持提取来自 message 和 callbackQuery 的 chatId。
     *
     * @param update 接收到的 Telegram Webhook 更新对象
     * @return 包含 chatId 的 Optional，如果找不到则为 Optional.empty()
     */
    public static Optional<Long> extractChatId(BotUpdateDto update) {
        if (update.message() != null && update.message().chat() != null) {
            return Optional.ofNullable(update.message().chat().id());
        }
        if (update.callbackQuery() != null &&
                update.callbackQuery().message() != null &&
                update.callbackQuery().message().chat() != null) {
            return Optional.ofNullable(update.callbackQuery().message().chat().id());
        }
        return Optional.empty();
    }

    /**
     * 尝试从 BotUpdateDto 中提取 chatType (e.g., 'private', 'group', 'supergroup').
     *
     * @param update 接收到的 Telegram Webhook 更新对象
     * @return 包含 chatType 的 Optional，如果找不到则为 Optional.empty()
     */
    public static Optional<String> extractChatType(BotUpdateDto update) {
        if (update.message() != null && update.message().chat() != null) {
            return Optional.ofNullable(update.message().chat().type());
        }
        if (update.callbackQuery() != null &&
                update.callbackQuery().message() != null &&
                update.callbackQuery().message().chat() != null) {
            return Optional.ofNullable(update.callbackQuery().message().chat().type());
        }
        return Optional.empty();
    }
}