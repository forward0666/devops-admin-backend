package com.backend.bot.handler;

import com.backend.bot.context.HandlerContext;
import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.service.BotClientService;
import com.backend.bot.service.UserSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(5)
public class CallbackQueryHandler extends AbstractUpdateHandler {

    private final BotClientService botClientService;
    private final UserSessionService userSessionService;
    private final List<CallbackActionHandler> actionHandlers;

    private static final int DEFAULT_ANSWER_DELAY_SECONDS = 10;
    private static final String DEFAULT_ANSWER_TEXT = "加载菜单 (%d秒销毁)...";

    @Override
    public boolean support(BotUpdateDto update) {
        return update.callbackQuery() != null;
    }

    @Override
    protected Mono<Void> handleUpdate(HandlerContext context, String logPrefix, ContextView contextView) {
        String token = context.token();
        String logIdentifier = context.logIdentifier();
        Long userId = context.userId();
        Long chatId = context.chatId();

        String callbackData = context.update().callbackQuery().data();
        String callbackQueryId = context.update().callbackQuery().id();

        log.info("{}⚙️ {} Received callback query: {}", logPrefix, logIdentifier, callbackData);

        Mono<Void> cancelTimerMono = userSessionService.cancelPendingDeletion(userId)
                .doOnSuccess(v -> log.debug("{}✅ User {} interaction detected. Canceled pending menu deletion timer.", logPrefix, userId))
                .onErrorResume(e -> Mono.empty());

        botClientService.answerCallbackQuery(token, callbackQueryId, String.format(DEFAULT_ANSWER_TEXT, DEFAULT_ANSWER_DELAY_SECONDS))
                .contextWrite(contextView)
                .subscribe(
                        null,
                        e -> log.error("{}❌ Failed to answer callback query for bot {}. Error: {}", logPrefix, logIdentifier, e.getMessage())
                );

        Mono<Void> mainExecutionMono = actionHandlers.stream()
                .sorted(Comparator.comparingInt(CallbackActionHandler::getOrder))
                .filter(handler -> handler.supports(callbackData))
                .findFirst()
                .map(handler -> {
                    log.info("{}🚀 {} Dispatching callback {} to handler: {}", logPrefix, logIdentifier, callbackData, handler.getClass().getSimpleName());
                    return handler.handle(context.botEntity(), context.update()).contextWrite(contextView);
                })
                .orElseGet(() -> {
                    return handleUnknownAction(token, chatId, callbackData, logIdentifier, logPrefix).contextWrite(contextView);
                });

        return cancelTimerMono.then(mainExecutionMono);
    }

    private Mono<Void> handleUnknownAction(String token, Long chatId, String callbackData, String logIdentifier, String logPrefix) {
        String responseText = String.format("⚠️ 您点击了未配置的菜单项或最终操作: %s", callbackData);
        log.warn("{}⚠️ {} No specific handler found for callback: {}. Sending default text response.", logPrefix, logIdentifier, callbackData);

        return botClientService.sendMessage(token, chatId, responseText, null)
                .onErrorResume(e -> Mono.empty())
                .then();
    }
}