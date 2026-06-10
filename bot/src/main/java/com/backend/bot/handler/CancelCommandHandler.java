package com.backend.bot.handler;

import com.backend.bot.context.HandlerContext;
import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.service.BotClientService;
import com.backend.bot.service.InteractiveMessageService;
import com.backend.bot.service.UserSessionService;
import com.backend.bot.util.BotUserUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

/**
 * 处理 /cancel 命令的处理器
 * 允许用户取消当前正在进行的操作
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(2) // 优先级低于 /start 命令
public class CancelCommandHandler extends AbstractUpdateHandler {

    private final BotClientService botClientService;
    private final UserSessionService userSessionService;
    private final InteractiveMessageService interactiveMessageService;
    private final ObjectMapper objectMapper;

    private static final String CANCEL_MESSAGE = "✅ 操作已取消。您可以使用 /start 命令重新开始。";
    private static final String NO_SESSION_MESSAGE = "请使用 /start 命令。";
    private static final int DELETE_DELAY_SECONDS = 5; // 5秒后删除取消消息

    @Override
    public boolean support(BotUpdateDto update) {
        return update.message() != null &&
                update.message().text() != null &&
                update.message().text().trim().startsWith("/cancel");
    }

    @Override
    protected Mono<Void> handleUpdate(HandlerContext context, String logPrefix, ContextView contextView) {
        String token = context.token();
        Long chatId = context.chatId();
        Long userId = context.userId();

        // 使用工具类获取标准化的身份日志
        String identityLog = BotUserUtils.formatIdentityLog(context);

        log.info("{}✅ Handling /cancel command. Identity: {}", logPrefix, identityLog);

        // 1. 检查用户是否有活动会话
        Mono<String> messageMono = userSessionService.getUserSession(userId, context.botEntity().getBotName())
                .map(session -> {
                    String state = session.getState();
                    log.info("{}⚠️ User {} cancelled operation with state: {}", logPrefix, userId, state);
                    
                    // 清除用户会话 (包括 Redis 键和内存中的 Disposable)
                    userSessionService.clearUserSession(userId).subscribe();
                    return CANCEL_MESSAGE;
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // 如果没有找到会话，记录一条信息
                    log.info("{}ℹ️ User {} executed /cancel but had no active session.", logPrefix, userId);
                    return Mono.just(NO_SESSION_MESSAGE);
                }));

        // 2. 发送消息并调度删除
        return messageMono.flatMap(message ->
                // 使用 sendMenuMessageWithResponse 方法，它返回响应对象，我们可以从中提取消息ID
                botClientService.sendMenuMessageWithResponse(token, chatId, message, null)
        ).doOnNext(responseJson -> {
            // 尝试解析响应 JSON 并调度删除
            try {
                JsonNode root = objectMapper.readTree(responseJson);
                JsonNode resultNode = root.path("result");

                if (!root.path("ok").asBoolean() || resultNode.isMissingNode()) {
                    log.error("{}❌ API failure for cancel message. JSON: {}", logPrefix, root.toPrettyString());
                    return;
                }

                Long messageId = resultNode.path("message_id").asLong(0);
                if (messageId != 0) {
                    log.info("{}⏳ Scheduling cancel message deletion (ID: {}) in {} seconds.", logPrefix, messageId, DELETE_DELAY_SECONDS);

                    // 调度删除任务
                    // 对非交互式取消消息，将 userId 设为 0L，防止 InteractiveMessageService 意外地清除用户的活跃会话。
                    interactiveMessageService.scheduleMessageDeletion(
                            token, 0L, chatId, messageId,
                            DELETE_DELAY_SECONDS, context.logIdentifier(), contextView
                    ).subscribe();
                }
            } catch (Exception e) {
                log.error("{}❌ Failed to parse cancel message response: {}", logPrefix, e.getMessage());
            }
        }).then();
    }
}