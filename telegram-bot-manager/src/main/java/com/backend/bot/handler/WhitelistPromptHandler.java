package com.backend.bot.handler;

import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.entity.BotConfigEntity;
import com.backend.bot.service.BotClientService;
import com.backend.bot.service.InteractiveMessageService;
import com.backend.bot.service.UserSessionService;
import com.backend.bot.util.LogUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import static com.backend.bot.constants.CallbackConstants.*;

/**
 * 专门处理最终的 IP 加白提示逻辑的处理器。
 * 职责：设置用户会话状态，编辑消息并移除键盘，提示用户输入 IP，并设置消息的自动销毁倒计时。
 * * 🚨 注意：为解决编译错误 ("位置: 类型为java.lang.Void的变量 message")，
 * 现假设 botClientService.editMessageText 返回 Mono<Void>，并使用 thenReturn() 传递原始 messageId。
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(20) // 优先级低于菜单导航
public class WhitelistPromptHandler implements CallbackActionHandler {

    private final BotClientService botClientService;
    private final UserSessionService userSessionService;
    private final InteractiveMessageService interactiveMessageService;

    // 文本常量
    private static final String IP_PROMPT_TEXT = "请提供IP及用户名，格式为：IP+用户名（e.g. 1.1.1.1+username）";
    private static final int TIMEOUT_SECONDS = 20; // 自动删除超时时间：20秒

    @Override
    public boolean supports(String callbackData) {
        // 支持所有最终的加白操作
        return callbackData.equals(FRONTEND_WEB_ACTION) ||
                callbackData.equals(FRONTEND_ADMIN_ACTION);
    }

    @Override
    public int getOrder() {
        return 20;
    }

    @Override
    public Mono<Void> handle(BotConfigEntity botEntity, BotUpdateDto botUpdate) {
        // 使用 deferContextual 捕获 Trace ID
        return Mono.deferContextual(contextView -> {
            final String traceLogPrefix = LogUtils.prepareMdcAndGetPrefix(contextView);

            // 提取关键信息
            String token = botEntity.getBotToken();
            Long chatId = botUpdate.callbackQuery().message().chat().id();
            Long userId = botUpdate.callbackQuery().from().id();
            Long messageId = botUpdate.callbackQuery().message().messageId();
            String callbackData = botUpdate.callbackQuery().data();

            String actionName = getActionName(callbackData);
            String sessionState = getSessionState(callbackData);
            String botLogIdentifier = String.format("[%s]", botEntity.getBotName());


            // 1. 🌟 设置会话状态
            Mono<Void> updateSessionMono = userSessionService.updateUserSession(userId, sessionState, messageId);

            // 2. 🌟 准备响应文本 (包含计时器提示)
            String newText = String.format("您选择了 **%s**，\n请回复此消息，输入以下格式信息：\n\n`%s`\n\n*此消息将在 %d 秒后自动删除。*", actionName, IP_PROMPT_TEXT, TIMEOUT_SECONDS);

            // 3. 🌟 编辑原始消息文本，并销毁键盘 (移除键盘)
            // 辅助方法已更新为返回 Mono<Long> (消息 ID)，以支持定时删除
            Mono<Long> messageIdToScheduleDeletionMono = editMessageTextAndRemoveMarkup(token, chatId, messageId, newText, actionName, traceLogPrefix);

            // 4. 组合 Mono：先更新状态 -> 执行编辑/发送 -> 调度删除任务
            return updateSessionMono
                    .then(messageIdToScheduleDeletionMono)
                    // 5. 调度自动删除任务
                    .flatMap(editedMessageId -> {
                        // 只有 ID 大于 0 才进行调度 (0L 表示提取 ID 失败)
                        if (editedMessageId > 0) {
                            return interactiveMessageService.scheduleMessageDeletion(
                                    token,
                                    userId,
                                    chatId,
                                    editedMessageId,
                                    TIMEOUT_SECONDS,
                                    botLogIdentifier,
                                    contextView
                            );
                        }
                        // 返回 Mono.empty() 结束链式调用
                        return Mono.empty();
                    })
                    .then();
        });
    }

    /**
     * 辅助方法：编辑消息文本，并显式销毁（移除）内联键盘。
     * * 修复了之前编译错误（messageId() 找不到符号）的逻辑：
     * 1. 假设 botClientService.editMessageText 返回 Mono<Void>，成功后返回原始 messageId。
     * 2. 假设 botClientService.sendMessage 返回一个 DTO，需要使用反射来安全地提取 messageId。
     * * @param traceLogPrefix 包含 MDC 追踪前缀，用于日志记录。
     * @return 返回 Mono<Long>，其中包含需要被删除的消息ID。
     */
    private Mono<Long> editMessageTextAndRemoveMarkup(String token, Long chatId, Long messageId, String newText, String actionName, String traceLogPrefix) {
        return botClientService.editMessageText(token, chatId, messageId, newText, null)
                // 1. 编辑成功，但 botClientService 返回 Mono<Void>，
                //    因此使用 thenReturn() 切换为返回原始消息ID (messageId) 进行删除调度。
                .thenReturn(messageId)
                .onErrorResume(e -> {
                    log.error("{}❌ Failed to edit message text for action: {}. Sending new message instead.", traceLogPrefix, actionName, e);
                    // 2. 如果编辑失败，发送新消息，并尝试从返回的 DTO 中提取新消息的 ID
                    //    🚨 使用反射来避免找不到符号的编译问题，但要求返回对象必须有 messageId() 方法
                    return botClientService.sendMessage(token, chatId, newText, null)
                            .map(message -> {
                                try {
                                    // 尝试通过反射调用 messageId() 方法来提取 ID
                                    return (Long) message.getClass().getMethod("messageId").invoke(message);
                                } catch (Exception ex) {
                                    log.error("{}🚨 Fatal: Cannot reflect messageId() from sendMessage result. Returning 0L.", traceLogPrefix, ex);
                                    return 0L;
                                }
                            })
                            // 再次处理发送新消息也失败的情况
                            .onErrorReturn(0L);
                });
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