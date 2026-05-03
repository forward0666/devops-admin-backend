package com.backend.bot.handler;

import com.backend.bot.context.HandlerContext;
import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.service.BotClientService;
import com.backend.bot.service.InteractiveMessageService;
import com.backend.bot.service.UserSessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

/**
 * 处理 /new 命令：清除当前会话并重新发起 /start
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(3)
public class NewCommandHandler extends AbstractUpdateHandler {

    private final BotClientService botClientService;
    private final UserSessionService userSessionService;
    private final InteractiveMessageService interactiveMessageService;
    private final ObjectMapper objectMapper;
    private final StartCommandHandler startCommandHandler;

    @Override
    public boolean support(BotUpdateDto update) {
        return update.message() != null &&
                update.message().text() != null &&
                update.message().text().trim().matches("/new($|@.+$)");
    }

    @Override
    protected Mono<Void> handleUpdate(HandlerContext context, String logPrefix, ContextView contextView) {
        String token = context.token();
        Long chatId = context.chatId();
        Long userId = context.userId();

        log.info("{}🔄 Handling /new command. userId={}, chatId={}", logPrefix, userId, chatId);

        // 清除旧会话
        userSessionService.clearUserSession(userId).subscribe();

        // 重新走 /start 流程
        return startCommandHandler.handleUpdate(context, logPrefix, contextView);
    }
}
