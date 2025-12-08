package com.backend.bot.handler;

import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.dto.InlineKeyboardMarkupDto;
import com.backend.bot.entity.BotConfigEntity;
import com.backend.bot.service.BotClientService;
import com.backend.bot.service.UserSessionService;
import com.backend.bot.template.MenuType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

/**
 * 负责处理来自 Telegram 内联键盘的回调查询（按钮点击）。
 * 核心功能：
 * 1. 取消旧菜单的自动销毁计时器。
 * 2. 立即响应回调，消除按钮上的加载动画。
 * 3. 🌟 新策略：对于菜单跳转，始终编辑当前消息，并根据菜单级别重置计时器。
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(5)
public class CallbackQueryHandler implements UpdateHandler {

    private final BotClientService botClientService;
    private final UserSessionService userSessionService;
    private final ObjectMapper objectMapper; // 保留，以防未来需要解析复杂 JSON

    // 🌟 自动销毁常量
    private static final int SECONDARY_MENU_DELETE_DELAY_SECONDS = 10;
    private static final int PRIMARY_MENU_DELETE_DELAY_SECONDS = 5;
    private static final String MENU_PROMPT_TEXT = "请在 %d 秒内完成操作：";
    private static final String MAIN_MENU_BACK = "MAIN_MENU_BACK";
    private static final String MAIN_MENU_CALLBACK = "MAIN_MENU";      // MenuType 中返回主菜单的根回调，用于判断计时器时间

    // 状态常量 (用于 SessionService)
    private static final String FRONTEND_ACTION = "callback_data_FRONTEND_DOMAIN_ACTION";
    private static final String BACKEND_ACTION = "callback_data_BACKEND_DOMAIN_ACTION";
    private static final String MIDDLEWARE_ACTION = "callback_data_MIDDLEWARE_DOMAIN_ACTION";
    private static final String IP_PROMPT_TEXT = "请提供IP及用户名，格式为：IP+用户名（e.g. 1.1.1.1+username）";

    public static final String STATE_AWAITING_FRONTEND_IP = "AWAITING_FRONTEND_IP";
    public static final String STATE_AWAITING_BACKEND_IP = "AWAITING_BACKEND_IP";
    public static final String STATE_AWAITING_MIDDLEWARE_IP = "AWAITING_MIDDLEWARE_IP";


    @Override
    public boolean support(BotUpdateDto update) {
        return update.callbackQuery() != null;
    }

    @Override
    public Mono<Void> handle(BotConfigEntity botEntity, BotUpdateDto botUpdate) {
        String token = botEntity.getBotToken();
        String botName = botEntity.getBotName();
        String logIdentifier = String.format("[%s]", botName);

        String callbackData = botUpdate.callbackQuery().data();
        Long chatId = botUpdate.callbackQuery().message().chat().id();
        Long userId = botUpdate.callbackQuery().from().id();
        Long messageId = botUpdate.callbackQuery().message().messageId(); // 当前被点击的消息 ID
        String callbackQueryId = botUpdate.callbackQuery().id();

        log.info("⚙️ {} Received callback query: {}", logIdentifier, callbackData);

        // 1. 用户交互发生，取消可能存在的自动删除计时器 (无论是 5s 还是 10s 的任务都会被取消)。
        Mono<Void> cancelTimerMono = userSessionService.cancelPendingDeletion(userId)
                .doOnSuccess(v -> log.debug("✅ User {} interaction detected. Canceled pending menu deletion timer.", userId))
                .onErrorResume(e -> Mono.empty());


        // --- 2. 立即响应 callback_query ---
        InlineKeyboardMarkupDto newMarkup = MenuType.createDynamicKeyboard(callbackData);

        // 动态计算 answerText 中的计时器时间
        int delay = getDeletionDelay(callbackData);
        String answerText = (newMarkup != null) ?
                String.format("加载菜单 (%d秒销毁)...", delay) :
                "操作执行中...";

        botClientService.answerCallbackQuery(token, callbackQueryId, answerText)
                .subscribe(
                        null,
                        e -> log.error("❌ Failed to answer callback query for bot {}. Error: {}", logIdentifier, e.getMessage())
                );

        // --- 3. 执行核心业务逻辑 ---
        Mono<Void> mainExecutionMono;

        if (newMarkup != null) {
            // 3.1 菜单跳转逻辑 (下钻或返回)：编辑当前消息，并重置计时器
            mainExecutionMono = handleMenuNavigationByEditing(token, chatId, userId, messageId, logIdentifier, callbackData, newMarkup);
        } else {
            // 3.2 最终操作逻辑 (MenuType 返回 null)

            mainExecutionMono = switch (callbackData) {
                case FRONTEND_ACTION, BACKEND_ACTION, MIDDLEWARE_ACTION -> handleIpWhitelistPrompt(token, chatId, userId, messageId, callbackData);

                default -> handleUnknownAction(token, chatId, callbackData, logIdentifier);
            };
        }

        // 4. 组合 Mono：先取消计时器，再执行主逻辑
        return cancelTimerMono.then(mainExecutionMono).then();
    }

    /**
     * 🌟 统一处理菜单导航（下钻和返回）：编辑当前消息，并启动新的自动销毁任务。
     */
    private Mono<Void> handleMenuNavigationByEditing(String token, Long chatId, Long userId, Long messageId, String logIdentifier, String callbackData, InlineKeyboardMarkupDto newMarkup) {

        // 确定计时器时间
        int delaySeconds = getDeletionDelay(callbackData);
        String menuText = String.format(MENU_PROMPT_TEXT, delaySeconds);

        // 1. 编辑当前消息，更新键盘
        return botClientService.editMessageText(token, chatId, messageId, menuText, newMarkup)
                .doOnSuccess(response -> {
                    log.info("⏳ {} Edited menu (Message ID: {}). Scheduling auto-deletion in {}s.", logIdentifier, messageId, delaySeconds);

                    // 2. 安排自动删除任务 (针对已编辑的同一消息 ID)
                    Disposable deletionTask = Mono.delay(Duration.ofSeconds(delaySeconds))
                            .flatMap(aLong -> {
                                log.warn("⏰ {} Auto-deleting menu message {} after {}s timeout.", logIdentifier, messageId, delaySeconds);
                                // 尝试删除消息，然后清理会话中的 Disposable 引用
                                return botClientService.deleteMessage(token, chatId, messageId)
                                        .then(userSessionService.cancelPendingDeletion(userId));
                            })
                            .subscribeOn(Schedulers.parallel())
                            .subscribe();

                    // 3. 存储任务引用
                    userSessionService.storePendingDeletion(userId, deletionTask).subscribe();
                })
                .onErrorResume(e -> {
                    log.error("❌ {} Failed to edit message (ID: {}) for navigation. Sending new /start prompt.", logIdentifier, messageId, e);
                    // 如果编辑失败（消息太旧），提示用户重新开始
                    return botClientService.sendMessage(token, chatId, "菜单操作失败或消息过旧，请重新 /start。", null);
                })
                .then();
    }


    /**
     * 处理域名加白提示逻辑：设置用户状态，然后编辑消息，删除键盘，并显示提示文本。
     */
    private Mono<Void> handleIpWhitelistPrompt(String token, Long chatId, Long userId, Long messageId, String callbackData) {
        String actionName = getActionName(callbackData);
        String sessionState = getSessionState(callbackData);

        // 1. 🌟 设置会话状态
        Mono<Void> updateSessionMono = userSessionService.updateUserSession(userId, sessionState, messageId);

        // 2. 🌟 准备响应文本 (使用 Markdown)
        String newText = String.format("您选择了 **%s**，\n请回复此消息，输入以下格式信息：\n\n`%s`", actionName, IP_PROMPT_TEXT);

        // 3. 🌟 编辑原始消息文本，并销毁键盘 (移除键盘，不需要新的计时器)
        Mono<Void> sendPromptMono = editMessageTextAndRemoveMarkup(token, chatId, messageId, newText, actionName);

        // 4. 组合 Mono，确保先更新状态再发送提示
        return updateSessionMono.then(sendPromptMono).then();
    }

    /**
     * 辅助方法：编辑消息文本，并显式销毁（移除）内联键盘。
     */
    private Mono<Void> editMessageTextAndRemoveMarkup(String token, Long chatId, Long messageId, String newText, String actionName) {
        return botClientService.editMessageText(token, chatId, messageId, newText, null)
                .onErrorResume(e -> {
                    log.error("❌ Failed to edit message text for action: {}. Sending new message instead.", actionName, e);
                    return botClientService.sendMessage(token, chatId, newText, null);
                })
                .then();
    }

    /**
     * 处理未知回调或默认最终操作。
     */
    private Mono<Void> handleUnknownAction(String token, Long chatId, String callbackData, String logIdentifier) {
        String responseText = String.format("✅ 您点击了最终操作或未配置的菜单项: %s", callbackData);
        log.warn("⚠️ {} No specific handler for callback: {}. Sending default text response.", logIdentifier, callbackData);

        return botClientService.sendMessage(token, chatId, responseText, null)
                .onErrorResume(e -> Mono.empty())
                .then();
    }

    /**
     * 根据回调数据判断目标菜单的级别，返回对应的销毁延迟时间。
     */
    private int getDeletionDelay(String callbackData) {
        // 假设 MenuType 返回主菜单时，其回调数据会包含 MAIN_MENU_BACK 或 MAIN_MENU
        if (callbackData.equals(MAIN_MENU_BACK) || callbackData.startsWith(MAIN_MENU_CALLBACK)) {
            return PRIMARY_MENU_DELETE_DELAY_SECONDS;
        }
        // 所有其他菜单跳转（包括下钻）都视为二级或更深，使用 10 秒
        return SECONDARY_MENU_DELETE_DELAY_SECONDS;
    }

    private String getActionName(String callbackData) {
        return switch (callbackData) {
            case FRONTEND_ACTION -> "前台域名加白";
            case BACKEND_ACTION -> "后台域名加白";
            case MIDDLEWARE_ACTION -> "中间件域名加白";
            default -> "未知操作";
        };
    }

    private String getSessionState(String callbackData) {
        return switch (callbackData) {
            case FRONTEND_ACTION -> STATE_AWAITING_FRONTEND_IP;
            case BACKEND_ACTION -> STATE_AWAITING_BACKEND_IP;
            case MIDDLEWARE_ACTION -> STATE_AWAITING_MIDDLEWARE_IP;
            default -> null;
        };
    }
}