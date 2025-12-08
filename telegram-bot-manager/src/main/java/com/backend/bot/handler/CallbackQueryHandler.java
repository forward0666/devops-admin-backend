package com.backend.bot.handler;

import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.dto.InlineKeyboardMarkupDto;
import com.backend.bot.entity.BotConfigEntity;
import com.backend.bot.service.BotClientService;
import com.backend.bot.service.UserSessionService;
import com.backend.bot.template.MenuType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 负责处理来自 Telegram 内联键盘的回调查询（按钮点击）。
 * 核心功能：
 * 1. 取消 /start 消息的自动销毁计时器。
 * 2. 立即响应回调，消除按钮上的加载动画。
 * 3. 根据回调数据执行菜单跳转或最终业务操作。
 * 4. 对于最终操作（如加白），设置用户会话状态并提示用户输入。
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(5) // 优先级最高 (数字最小)
public class CallbackQueryHandler implements UpdateHandler {

    private final BotClientService botClientService;
    private final UserSessionService userSessionService;

    // 🌟 定义固定的响应文本和回调数据常量
    private static final String FRONTEND_ACTION = "callback_data_FRONTEND_DOMAIN_ACTION";
    private static final String BACKEND_ACTION = "callback_data_BACKEND_DOMAIN_ACTION";
    private static final String MIDDLEWARE_ACTION = "callback_data_MIDDLEWARE_DOMAIN_ACTION";
    private static final String IP_PROMPT_TEXT = "请提供IP及用户名，格式为：IP+用户名（e.g. 1.1.1.1+username）";

    // 🌟 定义状态常量 (用于 SessionService)
    public static final String STATE_AWAITING_FRONTEND_IP = "AWAITING_FRONTEND_IP";
    public static final String STATE_AWAITING_BACKEND_IP = "AWAITING_BACKEND_IP";
    public static final String STATE_AWAITING_MIDDLEWARE_IP = "AWAITING_MIDDLEWARE_IP";


    @Override
    public boolean support(BotUpdateDto update) {
        // 仅处理回调查询
        return update.callbackQuery() != null;
    }

    @Override
    public Mono<Void> handle(BotConfigEntity botEntity, BotUpdateDto botUpdate) {
        String token = botEntity.getBotToken();
        String botName = botEntity.getBotName();
        String logIdentifier = String.format("[%s]", botName);

        log.info("📥 {} Full Update DTO received: {}", logIdentifier, botUpdate);

        String callbackData = botUpdate.callbackQuery().data();
        Long chatId = botUpdate.callbackQuery().message().chat().id();
        Long userId = botUpdate.callbackQuery().from().id();
        Long messageId = botUpdate.callbackQuery().message().messageId();
        String callbackQueryId = botUpdate.callbackQuery().id();

        log.info("⚙️ {} Received callback query: {}", logIdentifier, callbackData);

        // 🌟 1. 用户交互发生，取消可能存在的自动删除计时器。
        // 如果 StartCommandHandler 安排了删除任务，此处将取消它。
        Mono<Void> cancelTimerMono = userSessionService.cancelPendingDeletion(userId)
                .doOnSuccess(v -> log.debug("✅ User {} interaction detected. Canceled pending menu deletion timer.", userId))
                .onErrorResume(e -> {
                    log.error("❌ Failed to cancel pending deletion for user {}. Error: {}", userId, e.getMessage());
                    return Mono.empty(); // 错误不影响主流程
                });


        // --- 2. 立即响应 callback_query (Fire-and-forget) ---
        InlineKeyboardMarkupDto newMarkup = null;
        String answerText = newMarkup != null ? "加载菜单..." : "操作执行中...";
        botClientService.answerCallbackQuery(token, callbackQueryId, answerText)
                .subscribe(
                        null,
                        e -> log.error("❌ Failed to answer callback query for bot {}. Error: {}", logIdentifier, e.getMessage())
                );

        // --- 3. 执行核心业务逻辑 ---
        Mono<Void> mainExecutionMono;

        // 尝试获取下一个键盘
        newMarkup = MenuType.createDynamicKeyboard(callbackData);

        if (newMarkup != null) {
            // 3.1 菜单跳转/返回逻辑：编辑消息的键盘
            log.info("🔄 {} Editing message {} to display new menu based on callback: {}", logIdentifier, messageId, callbackData);

            mainExecutionMono = botClientService.editMessageReplyMarkup(token, chatId, messageId, newMarkup)
                    .onErrorResume(e -> {
                        log.error("❌ Failed to edit message reply markup for bot {}. Error: {}", logIdentifier, e.getMessage(), e);
                        // 如果编辑失败，通常是因为消息太旧，发送新消息提示用户重新开始
                        return botClientService.sendMessage(token, chatId, "菜单操作失败或消息过旧，请重新 /start。", null);
                    });

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
     * 处理域名加白提示逻辑：设置用户状态，然后编辑消息，删除键盘，并显示提示文本。
     */
    private Mono<Void> handleIpWhitelistPrompt(String token, Long chatId, Long userId, Long messageId, String callbackData) {
        String actionName = getActionName(callbackData);
        String sessionState = getSessionState(callbackData);

        // 1. 🌟 设置会话状态
        Mono<Void> updateSessionMono = userSessionService.updateUserSession(userId, sessionState, messageId);

        // 2. 🌟 准备响应文本 (使用 Markdown)
        String newText = String.format("您选择了 **%s**，\n请回复此消息，输入以下格式信息：\n\n`%s`", actionName, IP_PROMPT_TEXT);

        // 3. 🌟 编辑原始消息文本，并销毁键盘
        Mono<Void> sendPromptMono = editMessageTextAndRemoveMarkup(token, chatId, messageId, newText, actionName);

        // 4. 组合 Mono，确保先更新状态再发送提示
        return updateSessionMono.then(sendPromptMono).then();
    }

    /**
     * 辅助方法：编辑消息文本，并显式销毁（移除）内联键盘。
     */
    private Mono<Void> editMessageTextAndRemoveMarkup(String token, Long chatId, Long messageId, String newText, String actionName) {
        // 关键点：将 replyMarkup 设置为 null，这会移除当前消息上的键盘。
        return botClientService.editMessageText(token, chatId, messageId, newText, null)
                .onErrorResume(e -> {
                    // 如果编辑失败，发送新消息
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
                .onErrorResume(e -> {
                    log.error("❌ Failed to send final text response for bot {}. Error: {}", logIdentifier, e.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    private String getActionName(String callbackData) {
        return switch (callbackData) {
            case FRONTEND_ACTION -> "前台域名加白";
            case BACKEND_ACTION -> "后台域名加白";
            case MIDDLEWARE_ACTION -> "中间件域名加白";
            default -> "未知操作";
        };
    }

    // 🌟 根据回调数据获取对应的会话状态
    private String getSessionState(String callbackData) {
        return switch (callbackData) {
            case FRONTEND_ACTION -> STATE_AWAITING_FRONTEND_IP;
            case BACKEND_ACTION -> STATE_AWAITING_BACKEND_IP;
            case MIDDLEWARE_ACTION -> STATE_AWAITING_MIDDLEWARE_IP;
            default -> null;
        };
    }
}