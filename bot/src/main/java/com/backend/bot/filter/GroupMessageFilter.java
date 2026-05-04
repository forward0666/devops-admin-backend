package com.backend.bot.filter;

import com.backend.bot.constants.TelegramConstants;
import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.service.GroupMessageCleanupService;
import com.backend.bot.util.BotChatUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class GroupMessageFilter {

    private final GroupMessageCleanupService cleanupService;

    public Mono<Void> filter(BotUpdateDto update, String botToken) {
        if (update.message() == null || update.message().chat() == null || update.message().text() == null) {
            return Mono.empty();
        }

        if (!BotChatUtils.isGroupChat(update.message().chat())) {
            return Mono.empty();
        }

        String text = update.message().text().trim();
        boolean isCommand = false;
        for (String cmd : TelegramConstants.GROUP_COMMANDS_TO_CLEANUP) {
            if (text.equals(cmd) || text.startsWith(cmd + " ")) {
                isCommand = true;
                break;
            }
        }

        if (!isCommand) {
            return Mono.empty();
        }

        Long messageId = update.message().messageId();
        Long chatId = update.message().chat().id();

        if (messageId != null && chatId != null) {
            return cleanupService.markMessageForCleanup(chatId, messageId, botToken);
        }

        return Mono.empty();
    }
}
