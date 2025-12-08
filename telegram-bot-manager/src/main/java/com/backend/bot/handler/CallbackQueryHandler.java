package com.backend.bot.handler;

import com.backend.bot.context.HandlerContext;
import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.entity.BotConfigEntity;
import com.backend.bot.service.BotClientService;
import com.backend.bot.service.UserSessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;

/**
 * 职责：回调查询的总调度器（Dispatcher）。
 * 核心功能：
 * 1. 拦截所有 CallbackQuery。
 * 2. 统一处理用户交互：取消旧计时器、立即响应回调查询。
 * 3. 将具体业务逻辑（菜单跳转、业务操作）委派给 CallbackActionHandler 列表。
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(5)
public class CallbackQueryHandler implements UpdateHandler {

    private final BotClientService botClientService;
    private final UserSessionService userSessionService;
    private final ObjectMapper objectMapper;

    // 🌟 注入所有具体的 Handler 实例
    private final List<CallbackActionHandler> actionHandlers;

    // 默认的延迟时间（用于 answerCallbackQuery 的提示）
    private static final int DEFAULT_ANSWER_DELAY_SECONDS = 10;
    private static final String DEFAULT_ANSWER_TEXT = "加载菜单 (%d秒销毁)...";


    @Override
    public boolean support(BotUpdateDto update) {
        return update.callbackQuery() != null;
    }

    @Override
    public Mono<Void> handle(BotConfigEntity botEntity, BotUpdateDto botUpdate) {
        // 🌟 使用 HandlerContext 简化数据提取
        HandlerContext context = new HandlerContext(botEntity, botUpdate);

        String token = context.token();
        String logIdentifier = context.logIdentifier();
        Long userId = context.userId();
        Long chatId = context.chatId();

        String callbackData = botUpdate.callbackQuery().data();
        String callbackQueryId = botUpdate.callbackQuery().id();

        log.info("⚙️ {} Received callback query: {}", logIdentifier, callbackData);

        // 1. 用户交互发生，取消可能存在的自动删除计时器。
        // 由于所有菜单逻辑已拆分，这里只负责取消，后续的 Handler 负责设置。
        Mono<Void> cancelTimerMono = userSessionService.cancelPendingDeletion(userId)
                .doOnSuccess(v -> log.debug("✅ User {} interaction detected. Canceled pending menu deletion timer.", userId))
                .onErrorResume(e -> Mono.empty());


        // --- 2. 立即响应 callback_query ---
        // 调度器不再判断具体延迟时间，使用默认提示文本
        botClientService.answerCallbackQuery(token, callbackQueryId, String.format(DEFAULT_ANSWER_TEXT, DEFAULT_ANSWER_DELAY_SECONDS))
                .subscribe(
                        null,
                        e -> log.error("❌ Failed to answer callback query for bot {}. Error: {}", logIdentifier, e.getMessage())
                );

        // --- 3. 核心调度逻辑：查找并执行第一个支持该回调的 Handler ---
        // 对注入的 Handler 进行排序，确保执行顺序（如菜单导航优先于最终操作）
        Mono<Void> mainExecutionMono = actionHandlers.stream()
                .sorted(Comparator.comparingInt(CallbackActionHandler::getOrder))
                .filter(handler -> handler.supports(callbackData))
                .findFirst()
                .map(handler -> {
                    log.info("🚀 {} Dispatching callback {} to handler: {}", logIdentifier, callbackData, handler.getClass().getSimpleName());
                    return handler.handle(botEntity, botUpdate);
                })
                .orElseGet(() -> {
                    // 如果没有 Handler 支持，执行默认操作
                    return handleUnknownAction(token, chatId, callbackData, logIdentifier);
                });


        // 4. 组合 Mono：先取消计时器，再执行主逻辑
        return cancelTimerMono.then(mainExecutionMono).then();
    }

    /**
     * 处理未知回调或默认最终操作。
     */
    private Mono<Void> handleUnknownAction(String token, Long chatId, String callbackData, String logIdentifier) {
        String responseText = String.format("⚠️ 您点击了未配置的菜单项或最终操作: %s", callbackData);
        log.warn("⚠️ {} No specific handler found for callback: {}. Sending default text response.", logIdentifier, callbackData);

        return botClientService.sendMessage(token, chatId, responseText, null)
                .onErrorResume(e -> Mono.empty())
                .then();
    }
}