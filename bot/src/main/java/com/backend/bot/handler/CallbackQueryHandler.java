package com.backend.bot.handler;

import com.backend.bot.constants.TelegramConstants;
import com.backend.bot.context.HandlerContext;
import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.entity.UserSessionEntity;
import com.backend.bot.service.BotClientService;
import com.backend.bot.service.InteractiveMessageService;
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
    private final InteractiveMessageService interactiveMessageService;
    private final List<CallbackActionHandler> actionHandlers;

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

        log.info("{}⚙️ {} [Step1] 收到callback | data={}, userId={}, chatId={}", logPrefix, logIdentifier, callbackData, userId, chatId);

        boolean isMenuNavigation = actionHandlers.stream()
                .anyMatch(handler -> {
                    boolean supported = handler.supports(callbackData);
                    if (supported) log.info("{}🔍 isMenuNavigation check: {} supports={}", logPrefix, handler.getClass().getSimpleName(), callbackData);
                    return (handler instanceof MenuNavigationHandler || handler instanceof CachePurgeHandler) && supported;
                });

        // ⚠️ 关键：flatMap 里返回 Mono<Void> 也会触发 switchIfEmpty（因为 Mono<Void> 不发射元素）
        // 用 hasSession boolean 避免这个问题
        return userSessionService.getUserSession(userId)
                .map(session -> true)
                .defaultIfEmpty(false)
                .flatMap(hasSession -> {
                    if (hasSession) {
                        return userSessionService.getUserSession(userId)
                                .flatMap(session -> {
                                    String state = session.getState();
                                    if (TelegramConstants.SESSION_STATE_PROCESSING_START.equals(state)) {
                                        if (isMenuNavigation) {
                                            log.info("{}⚠️ User {} is processing /start but clicked navigation: {}. Allowing.", logPrefix, userId, callbackData);
                                        } else {
                                            log.info("{}⚠️ User {} is processing /start. Ignoring: {}", logPrefix, userId, callbackData);
                                            return botClientService.answerCallbackQuery(token, callbackQueryId, "⏳ 正在处理您的请求，请稍候...");
                                        }
                                    }
                                    return processCallbackNormally(context, logPrefix, contextView, logIdentifier, userId, callbackData, callbackQueryId);
                                });
                    } else {
                        // 没有会话
                        if (isMenuNavigation) {
                            log.info("{}⚠️ User {} has no session, clicked navigation: {}. Creating temp session.", logPrefix, userId, callbackData);
                            return userSessionService.updateUserSession(userId, "TEMPORARY_SESSION", null)
                                    .contextWrite(contextView)
                                    .then(processCallbackNormally(context, logPrefix, contextView, logIdentifier, userId, callbackData, callbackQueryId));
                        } else {
                            return botClientService.answerCallbackQuery(token, callbackQueryId, "⚠️ 没有活跃会话，请使用 /start 开始");
                        }
                    }
                });
    }

    private Mono<Void> processCallbackNormally(HandlerContext context, String logPrefix, ContextView contextView,
                                               String logIdentifier, Long userId, String callbackData, String callbackQueryId) {
        String token = context.token();
        Long chatId = context.chatId();

        Long messageId = context.messageId();
        int delaySeconds = callbackData.contains("_ACTION") ? 30 : TelegramConstants.MENU_DELETE_DELAY_SECONDS;
        log.info("{}⏳ [Step2] 设置删除定时器 | messageId={}, delay={}s", logPrefix, messageId, delaySeconds);
        Mono<Void> deleteTimerMono = messageId != null
                ? interactiveMessageService.scheduleMessageDeletion(
                        token, userId, chatId, messageId, delaySeconds, logIdentifier, contextView
                ).contextWrite(contextView).onErrorResume(e -> Mono.empty())
                : Mono.empty();

        // 回答回调查询 - fire and forget
        botClientService.answerCallbackQuery(token, callbackQueryId)
                .contextWrite(contextView)
                .subscribe(null, e -> log.error("{}❌ Failed to answer callback: {}", logPrefix, e.getMessage()));

        // 执行回调处理器
        Mono<Void> handlerMono = actionHandlers.stream()
                .sorted(Comparator.comparingInt(CallbackActionHandler::getOrder))
                .filter(handler -> handler.supports(callbackData))
                .findFirst()
                .map(handler -> {
                    log.info("{}🚀 {} Dispatching {} to {}", logPrefix, logIdentifier, callbackData, handler.getClass().getSimpleName());
                    return handler.handle(context.botEntity(), context.update()).contextWrite(contextView);
                })
                .orElseGet(() -> handleUnknownAction(token, chatId, callbackData, logIdentifier, logPrefix).contextWrite(contextView));

        return deleteTimerMono
                .doOnSuccess(v -> log.info("{}✅ [Step3] 删除定时器设置完成 | messageId={}", logPrefix, messageId))
                .then(handlerMono)
                .doOnSuccess(v -> log.info("{}✅ [Step4] Handler执行完成 | callbackData={}", logPrefix, callbackData))
                .then(userSessionService.clearUserSession(userId).contextWrite(contextView))
                .doOnSuccess(v -> log.info("{}✅ [Step5] Session已清理 | userId={}", logPrefix, userId))
                .onErrorResume(e -> {
                    log.error("{}❌ [CallbackFlow] 处理失败 | error={}", logPrefix, e.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<Void> handleUnknownAction(String token, Long chatId, String callbackData, String logIdentifier, String logPrefix) {
        String responseText = String.format("⚠️ 您点击了未配置的菜单项: %s", callbackData);
        log.warn("{}⚠️ {} No handler for callback: {}", logPrefix, logIdentifier, callbackData);
        return botClientService.sendMessage(token, chatId, responseText, null)
                .onErrorResume(e -> Mono.empty())
                .then();
    }
}
