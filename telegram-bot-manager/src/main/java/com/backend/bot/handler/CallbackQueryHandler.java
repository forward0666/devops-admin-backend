package com.backend.bot.handler;

import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.dto.InlineKeyboardMarkupDto;
import com.backend.bot.entity.BotConfigEntity;
import com.backend.bot.service.BotClientService;
// 假设你有一个 SessionService
import com.backend.bot.service.UserSessionService;
import com.backend.bot.template.MenuType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class CallbackQueryHandler implements UpdateHandler {

    private final BotClientService botClientService;
    // 假设你有一个会话服务
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
        return update.callbackQuery() != null;
    }

    @Override
    public Mono<Void> handle(BotConfigEntity botEntity, BotUpdateDto botUpdate) {
        String token = botEntity.getBotToken();
        String botName = botEntity.getBotName();
        String logIdentifier = String.format("[%s]", botName);

        // 【新增】打印整个接收到的 BotUpdateDto，以便完整查看信息
        log.info("📥 {} Full Update DTO received: {}", logIdentifier, botUpdate);

        String callbackData = botUpdate.callbackQuery().data();
        Long chatId = botUpdate.callbackQuery().message().chat().id();
        Long userId = botUpdate.callbackQuery().from().id(); // 🌟 获取用户 ID
        Long messageId = botUpdate.callbackQuery().message().messageId();
        String callbackQueryId = botUpdate.callbackQuery().id();

        log.info("⚙️ {} Received callback query: {}", logIdentifier, callbackData);

        // --- 1. 生成新的键盘（MenuType 返回 null 即为最终操作） ---
        InlineKeyboardMarkupDto newMarkup = MenuType.createDynamicKeyboard(callbackData);

        // --- 2. 立即响应 callback_query (Fire-and-forget) ---
        String answerText = newMarkup != null ? "加载菜单..." : "操作执行中...";
        botClientService.answerCallbackQuery(token, callbackQueryId, answerText)
                .subscribe(
                        null,
                        e -> log.error("❌ Failed to answer callback query for bot {}. Error: {}", logIdentifier, e.getMessage())
                );

        // --- 3. 执行核心业务逻辑 ---
        if (newMarkup != null) {
            // 3.1 菜单跳转/返回逻辑
            log.info("🔄 {} Editing message {} to display new menu based on callback: {}", logIdentifier, messageId, callbackData);

            return botClientService.editMessageReplyMarkup(token, chatId, messageId, newMarkup)
                    .onErrorResume(e -> {
                        log.error("❌ Failed to edit message reply markup for bot {}. Error: {}", logIdentifier, e.getMessage(), e);
                        return botClientService.sendMessage(token, chatId, "菜单操作失败或消息过旧，请重新 /start。", null);
                    })
                    .then();

        } else {
            // 3.2 最终操作逻辑 (MenuType 返回 null)

            return switch (callbackData) {
                case FRONTEND_ACTION, BACKEND_ACTION, MIDDLEWARE_ACTION -> handleIpWhitelistPrompt(token, chatId, userId, messageId, callbackData);

                default -> handleUnknownAction(token, chatId, callbackData, logIdentifier);
            };
        }
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

        // 3. 🌟 尝试编辑原始消息文本，并附带 ForceReply 键盘
        // 简化起见，我们先使用 editMessageText 移除键盘并发送文本，让 TextHandler 通过 SessionService 捕获后续消息。
        Mono<Void> sendPromptMono = botClientService.editMessageText(token, chatId, messageId, newText, null)
                .onErrorResume(e -> {
                    // 如果编辑失败，发送新消息
                    log.error("❌ Failed to edit message text for action: {}. Sending new message instead.", actionName, e);
                    return botClientService.sendMessage(token, chatId, newText, null);
                });

        // 4. 组合 Mono，确保先更新状态再发送提示
        return updateSessionMono.then(sendPromptMono)
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