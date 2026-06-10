package com.backend.bot.handler;

import com.backend.bot.constants.TelegramConstants;
import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.dto.UserDto;
import com.backend.bot.entity.BotConfigEntity;
import com.backend.bot.service.BotClientService;
import com.backend.bot.service.InteractiveMessageService;
import com.backend.bot.service.UserSessionService;
import com.backend.bot.util.BotUserUtils;
import com.backend.bot.util.LogUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;

import static com.backend.bot.constants.CallbackConstants.*;

/**
 * 专门处理最终的 IP 加白提示逻辑的处理器。
 * 职责：设置用户会话状态，编辑消息并移除键盘，提示用户输入 IP，并设置消息的自动销毁倒计时。
 * * 逻辑：无论用户后续是否回复，该提示消息（IP_PROMPT_TEXT）都会在 TIMEOUT_SECONDS 后尝试删除。
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(20)
public class WhitelistPromptHandler implements CallbackActionHandler {

    private final BotClientService botClientService;
    private final UserSessionService userSessionService;
    private final InteractiveMessageService interactiveMessageService;

    // 使用中央常量定义，确保配置统一
    private static final String IP_PROMPT_TEXT = TelegramConstants.IP_INPUT_PROMPT;
    private static final int TIMEOUT_SECONDS = TelegramConstants.IP_INPUT_TIMEOUT_SECONDS;

    @Override
    public boolean supports(String callbackData) {
        return callbackData.equals(FRONTEND_WEB_ACTION) ||
                callbackData.equals(FRONTEND_ADMIN_ACTION);
    }

    @Override
    public int getOrder() {
        return 20;
    }

    @Override
    public Mono<Void> handle(BotConfigEntity botEntity, BotUpdateDto botUpdate) {
        return Mono.deferContextual(contextView -> {
            final String traceLogPrefix = LogUtils.prepareMdcAndGetPrefix(contextView);

            // 提取关键信息
            String token = botEntity.getBotToken();
            Long chatId = botUpdate.callbackQuery().message().chat().id();
            UserDto operatorUser = botUpdate.callbackQuery().from();
            Long userId = operatorUser.id();

            // --- 提取操作人名称：使用 BotUserUtils 工具类 ---
            final String finalOperatorName = BotUserUtils.getOperatorName(operatorUser, userId);
            // ------------------------------------------------

            Long originalMessageId = botUpdate.callbackQuery().message().messageId();
            String callbackData = botUpdate.callbackQuery().data();

            String actionName = getActionName(callbackData);
            String sessionState = getSessionState(callbackData);
            String botLogIdentifier = String.format("[%s]", botEntity.getBotName());

            // 1. 🌟 设置会话状态 (为了接收用户的后续回复)
            // 这里将 originalMessageId 存入会话，以便后续 TextUpdateHandler 删除提示消息
            Mono<Void> updateSessionMono = userSessionService.updateUserSession(userId, sessionState, originalMessageId, botEntity.getBotName());

            // 2. 🌟 准备响应文本 (包含操作人信息和倒计时提示)
            String newText = String.format(" **%s** 选择了 **%s**，\n请回复此消息，输入以下格式信息：\n\n`%s`\n\n*⏳ 此提示消息将在 %d 秒后自动销毁，请尽快操作。*",
                    finalOperatorName, actionName, IP_PROMPT_TEXT, TIMEOUT_SECONDS); // <-- 使用 finalOperatorName

            // 3. 🌟 编辑消息或发送新消息，并获取最终展示的消息ID
            Mono<Long> displayMessageMono = editMessageTextAndRemoveMarkup(token, chatId, originalMessageId, newText, actionName, traceLogPrefix);

            // 4. 组合执行流
            return updateSessionMono
                    .then(displayMessageMono)
                    .flatMap(activeMessageId -> {
                        // 如果获取到了有效的消息ID (无论是编辑旧的还是发送新的)
                        if (activeMessageId != null && activeMessageId > 0) {
                            log.info("{}⏳ Scheduling deletion for messageId: {} in {} seconds.", traceLogPrefix, activeMessageId, TIMEOUT_SECONDS);

                            // 5. 🌟 核心：调度自动删除任务
                            return interactiveMessageService.scheduleMessageDeletion(
                                    token,
                                    userId,
                                    chatId,
                                    activeMessageId,
                                    TIMEOUT_SECONDS,
                                    botLogIdentifier,
                                    contextView
                            ).onErrorResume(e -> {
                                // 即使调度器报错，也不要影响主流程
                                log.warn("{} ⚠️ Failed to schedule message deletion: {}", traceLogPrefix, e.getMessage());
                                return Mono.empty();
                            });
                        }
                        return Mono.empty();
                    })
                    .then(); // 最终返回 Mono<Void>
        });
    }

    /**
     * 编辑旧消息，如果编辑失败则发送新消息。
     * @return 最终展示给用户的消息 ID
     */
    private Mono<Long> editMessageTextAndRemoveMarkup(String token, Long chatId, Long originalMessageId, String newText, String actionName, String traceLogPrefix) {
        // 尝试编辑
        return botClientService.editMessageText(token, chatId, originalMessageId, newText, null)
                // 如果编辑成功，直接返回原始 ID
                .thenReturn(originalMessageId)
                // 如果编辑失败（例如消息太旧），则回退到发送新消息
                .onErrorResume(e -> {
                    log.warn("{} ⚠️ Could not edit message ({}). Sending new message instead.", traceLogPrefix, e.getMessage());

                    return botClientService.sendMessage(token, chatId, newText, null)
                            .map(this::extractMessageIdSafe) // 安全提取 ID
                            .defaultIfEmpty(0L);
                });
    }

    /**
     * 安全地通过反射从返回对象中提取 messageId。
     * 解决了编译期找不到符号的问题。
     */
    private Long extractMessageIdSafe(Object messageObj) {
        if (messageObj == null) return 0L;
        try {
            // 假设对象中有 messageId() 方法 (Record类) 或 getMessageId() 方法 (JavaBean)
            Method method;
            try {
                method = messageObj.getClass().getMethod("messageId");
            } catch (NoSuchMethodException e) {
                method = messageObj.getClass().getMethod("getMessageId");
            }
            Object result = method.invoke(messageObj);
            return result instanceof Integer ? ((Integer) result).longValue() : (Long) result;
        } catch (Exception ex) {
            log.error("🚨 Fatal: Failed to reflect messageId from object: {}. Class: {}", messageObj, messageObj.getClass().getName(), ex);
            return 0L;
        }
    }

    private String getActionName(String callbackData) {
        return switch (callbackData) {
            case FRONTEND_WEB_ACTION -> "前端前台域名加白";
            case FRONTEND_ADMIN_ACTION -> "前端后台域名加白";
            default -> "未知操作";
        };
    }

    private String getSessionState(String callbackData) {
        return switch (callbackData) {
            case FRONTEND_WEB_ACTION -> STATE_AWAITING_FRONTEND_WEB_IP;
            case FRONTEND_ADMIN_ACTION -> STATE_AWAITING_FRONTEND_ADMIN_IP;
            default -> null;
        };
    }
}