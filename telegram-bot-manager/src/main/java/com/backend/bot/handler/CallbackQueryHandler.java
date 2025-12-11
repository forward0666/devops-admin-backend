package com.backend.bot.handler;

import com.backend.bot.constants.TelegramConstants;
import com.backend.bot.context.HandlerContext;
import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.entity.UserSessionEntity;
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

        // 检查是否是菜单导航操作
        boolean isMenuNavigation = actionHandlers.stream()
                .anyMatch(handler -> handler instanceof MenuNavigationHandler && handler.supports(callbackData));
        
        // 使用原子性检查和处理，避免重复处理
        return userSessionService.getUserSession(userId)
                .flatMap(session -> {
                    // 有会话的情况
                    String state = session.getState();
                    
                    // 如果用户正在处理/start命令，但点击的是菜单导航操作，允许执行
                    if (TelegramConstants.SESSION_STATE_PROCESSING_START.equals(state)) {
                        if (isMenuNavigation) {
                            log.info("{}⚠️ User {} is processing /start command but clicked menu navigation: {}. Allowing operation.", logPrefix, userId, callbackData);
                            // 允许菜单导航操作，继续执行后续流程，但不取消删除任务
                            return processCallbackWithoutCancel(context, logPrefix, contextView, logIdentifier, userId, callbackData, callbackQueryId);
                        } else {
                            // 非菜单导航操作，忽略并返回提示
                            log.info("{}⚠️ User {} is processing /start command. Ignoring callback action: {}", logPrefix, userId, callbackData);
                            // 只回答回调查询，不取消待删除任务，也不执行任何其他操作
                            return botClientService.answerCallbackQuery(token, callbackQueryId, "⏳ 正在处理您的请求，请稍候...")
                                    .then(); // 返回提示并结束流程
                        }
                    }
                    
                    // 正常流程：根据操作类型决定是否取消计时器
                    if (isMenuNavigation) {
                        // 菜单导航，不取消删除任务
                        return processCallbackWithoutCancel(context, logPrefix, contextView, logIdentifier, userId, callbackData, callbackQueryId);
                    } else {
                        // 其他操作，取消删除任务
                        return processCallbackNormally(context, logPrefix, contextView, logIdentifier, userId, callbackData, callbackQueryId);
                    }
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // 没有会话的情况
                    if (isMenuNavigation) {
                        // 创建临时会话并处理菜单导航
                        log.info("{}⚠️ User {} has no session, but clicked menu navigation: {}. Creating temporary session.", logPrefix, userId, callbackData);
                        return userSessionService.updateUserSession(userId, "TEMPORARY_SESSION", null)
                                .contextWrite(contextView)
                                .then(processCallbackWithoutCancel(context, logPrefix, contextView, logIdentifier, userId, callbackData, callbackQueryId))
                                // 临时会话需要保持一段时间以便消息自动删除，不立即清除
                                .then();
                    } else {
                        // 非菜单导航操作，提示用户
                        return botClientService.answerCallbackQuery(token, callbackQueryId, "⚠️ 没有活跃会话，请使用 /start 开始")
                                .then();
                    }
                }));
    }

    private Mono<Void> processCallbackNormally(HandlerContext context, String logPrefix, ContextView contextView, 
                                               String logIdentifier, Long userId, String callbackData, String callbackQueryId) {
        String token = context.token();
        Long chatId = context.chatId();
        
        // 1. 取消待删除任务
        Mono<Void> cancelTimerMono = userSessionService.cancelPendingDeletion(userId)
                .doOnSuccess(v -> log.debug("{}✅ User {} interaction detected. Canceled pending menu deletion timer.", logPrefix, userId))
                .onErrorResume(e -> Mono.empty());
        
        // 2. 回答回调查询 - 不显示加载提示，直接处理
        botClientService.answerCallbackQuery(token, callbackQueryId)
                .contextWrite(contextView)
                .subscribe(
                        null,
                        e -> log.error("{}❌ Failed to answer callback query for bot {}. Error: {}", logPrefix, logIdentifier, e.getMessage())
                );
        
        // 3. 执行回调处理器
        Mono<Void> handlerMono = actionHandlers.stream()
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
        
        // 按顺序执行：取消计时器 -> 执行处理器
        return cancelTimerMono.then(handlerMono);
    }
    
    private Mono<Void> processCallbackWithoutCancel(HandlerContext context, String logPrefix, ContextView contextView, 
                                                 String logIdentifier, Long userId, String callbackData, String callbackQueryId) {
        String token = context.token();
        Long chatId = context.chatId();
        
        // 1. 不取消待删除任务，让各个消息独立销毁
        log.debug("{}⏭️ User {} clicked menu navigation: {}. Not canceling deletion timers.", logPrefix, userId, callbackData);
        
        // 2. 回答回调查询 - 不显示加载提示，直接处理
        botClientService.answerCallbackQuery(token, callbackQueryId)
                .contextWrite(contextView)
                .subscribe(
                        null,
                        e -> log.error("{}❌ Failed to answer callback query for bot {}. Error: {}", logPrefix, logIdentifier, e.getMessage())
                );
        
        // 3. 执行回调处理器
        Mono<Void> handlerMono = actionHandlers.stream()
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
        
        // 只执行处理器，不取消计时器
        return handlerMono;
    }
    
    private Mono<Void> handleUnknownAction(String token, Long chatId, String callbackData, String logIdentifier, String logPrefix) {
        String responseText = String.format("⚠️ 您点击了未配置的菜单项或最终操作: %s", callbackData);
        log.warn("{}⚠️ {} No specific handler found for callback: {}. Sending default text response.", logPrefix, logIdentifier, callbackData);

        return botClientService.sendMessage(token, chatId, responseText, null)
                .onErrorResume(e -> Mono.empty())
                .then();
    }
}