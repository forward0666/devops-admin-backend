package com.backend.bot.handler;

import com.backend.bot.constants.CallbackConstants;
import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.entity.BotConfigEntity;
import com.backend.bot.service.BotClientService;
import com.backend.bot.service.UserSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import static com.backend.bot.constants.CallbackConstants.*;

/**
 * 专门处理最终的 IP 加白提示逻辑的处理器。
 * 职责：设置用户会话状态，编辑消息并移除键盘，提示用户输入 IP。
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(20) // 优先级低于菜单导航
public class WhitelistPromptHandler implements CallbackActionHandler {

    private final BotClientService botClientService;
    private final UserSessionService userSessionService;

    // 文本常量
    private static final String IP_PROMPT_TEXT = "请提供IP及用户名，格式为：IP+用户名（e.g. 1.1.1.1+username）";


    @Override
    public boolean supports(String callbackData) {
        // 支持所有最终的加白操作
        return callbackData.equals(FRONTEND_ACTION) ||
                callbackData.equals(BACKEND_ACTION) ||
                callbackData.equals(MIDDLEWARE_ACTION);
    }

    @Override
    public int getOrder() {
        return 20;
    }

    @Override
    public Mono<Void> handle(BotConfigEntity botEntity, BotUpdateDto botUpdate) {
        String token = botEntity.getBotToken();
        Long chatId = botUpdate.callbackQuery().message().chat().id();
        Long userId = botUpdate.callbackQuery().from().id();
        Long messageId = botUpdate.callbackQuery().message().messageId();
        String callbackData = botUpdate.callbackQuery().data();

        String actionName = getActionName(callbackData);
        String sessionState = getSessionState(callbackData);

        // 1. 🌟 设置会话状态
        Mono<Void> updateSessionMono = userSessionService.updateUserSession(userId, sessionState, messageId);

        // 2. 🌟 准备响应文本 (使用 Markdown)
        String newText = String.format("您选择了 **%s**，\n请回复此消息，输入以下格式信息：\n\n`%s`", actionName, IP_PROMPT_TEXT);

        // 3. 🌟 编辑原始消息文本，并销毁键盘 (移除键盘)
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