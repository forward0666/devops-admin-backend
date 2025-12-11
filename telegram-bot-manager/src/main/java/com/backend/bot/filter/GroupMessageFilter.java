package com.backend.bot.filter;

import com.backend.bot.constants.TelegramConstants;
import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.service.GroupMessageCleanupService;
import com.backend.bot.util.BotChatUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 群消息过滤器
 * * 在请求处理之前过滤群聊中的命令消息，将其标记为待清理。
 * 这样可以保持群聊的整洁性，避免命令消息堆积。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GroupMessageFilter {

    private final GroupMessageCleanupService groupMessageCleanupService;

    /**
     * 过滤群聊消息
     * * @param update Telegram 更新对象
     * @param botToken Bot Token
     * @return Mono<Void>
     */
    public Mono<Void> filter(BotUpdateDto update, String botToken) {
        return Mono.defer(() -> {
            // 检查是否是群聊消息
            if (isGroupChat(update)) {
                // 检查是否是命令消息
                if (isCommandMessage(update)) {
                    // 获取消息ID并标记为待清理
                    Long messageId = update.message() != null ? update.message().messageId() : null;
                    Long chatId = update.message() != null ? update.message().chat().id() : null;

                    if (messageId != null && chatId != null) {
                        groupMessageCleanupService.markMessageForCleanup(chatId, messageId, botToken);
                        log.debug("Marked group command message {} in chat {} for cleanup", messageId, chatId);
                    }
                }
            }
            return Mono.empty();
        });
    }

    /**
     * 检查是否是群聊
     * * @param update Telegram 更新对象
     * @return 如果是群聊返回true，否则返回false
     */
    private boolean isGroupChat(BotUpdateDto update) {
        if (update.message() == null || update.message().chat() == null) {
            return false;
        }

        // 使用 BotChatUtils 工具类判断是否是群聊
        return BotChatUtils.isGroupChat(update.message().chat());
    }

    /**
     * 检查是否是命令消息
     * * @param update Telegram 更新对象
     * @return 如果是命令消息返回true，否则返回false
     */
    private boolean isCommandMessage(BotUpdateDto update) {
        if (update.message() == null || update.message().text() == null) {
            return false;
        }

        String messageText = update.message().text().trim();

        // 使用常量数组检查消息是否是需要清理的命令
        for (String command : TelegramConstants.GROUP_COMMANDS_TO_CLEANUP) { // <-- 正确引用
            if (messageText.equals(command) || messageText.startsWith(command + " ")) {
                return true;
            }
        }

        return false;
    }
}