package com.backend.bot.handler;

import com.backend.bot.constants.TelegramConstants;
import com.backend.bot.context.HandlerContext;
import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.dto.InlineKeyboardMarkupDto;
import com.backend.bot.service.BotClientService;
import com.backend.bot.service.InteractiveMessageService;
import com.backend.bot.service.RedisUserSessionService;
import com.backend.bot.service.UserSessionService;
import com.backend.bot.service.BotMenuService;
import com.backend.bot.template.MenuType;
import com.backend.bot.util.BotUserUtils; // 引入新工具类
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(1)
public class StartCommandHandler extends AbstractUpdateHandler {

    private final BotClientService botClientService;
    private final InteractiveMessageService interactiveMessageService;
    private final UserSessionService userSessionService;
    private final RedisUserSessionService redisUserSessionService;
    private final ObjectMapper objectMapper;
    private final BotMenuService botMenuService;

    private static final String WELCOME_TEXT = TelegramConstants.WELCOME_MESSAGE;
    private static final int DELETE_DELAY_SECONDS = TelegramConstants.DEFAULT_DELETE_DELAY_SECONDS;

    // 🌟 警告消息常量
    private static final String WARNING_TEXT = "⚠️ 您有一个正在进行的操作，请完成当前操作或发送 /cancel 取消。";

    @Override
    public boolean support(BotUpdateDto update) {
        return update.message() != null &&
                update.message().text() != null &&
                update.message().text().trim().startsWith("/start");
    }

    @Override
    protected Mono<Void> handleUpdate(HandlerContext context, String logPrefix, ContextView contextView) {
        String token = context.token();
        Long chatId = context.chatId();
        Long userId = context.userId();

        // 🌟 优化点：直接调用工具类获取标准化的身份日志
        String identityLog = BotUserUtils.formatIdentityLog(context);

        log.info("{}✅ Handling /start command. Identity: {}", logPrefix, identityLog);

        // 检查 Redis 中是否存在用户会话标记（UserSession:1 或 UserSession:0）
        return redisUserSessionService.hasAnySession(userId)
                .flatMap(hasSession -> {
                    if (hasSession) {
                        // 用户有会话标记，说明用户当前正在进行操作
                        log.info("{}⚠️ User {} has an active session. Notifying user to complete current operation first.", logPrefix, userId);

                        // 🌟 核心修改：发送警告消息并调度自动删除
                        return botClientService.sendMenuMessageWithResponse(token, chatId, WARNING_TEXT, null)
                                .flatMap(responseJson -> {
                                    // 尝试解析响应 JSON 并调度删除
                                    try {
                                        // 使用 readTree() 解析 JSON 字符串
                                        JsonNode root = objectMapper.readTree(responseJson);
                                        JsonNode resultNode = root.path("result");

                                        if (!root.path("ok").asBoolean() || resultNode.isMissingNode()) {
                                            log.error("{}❌ API failure for warning message. JSON: {}", logPrefix, root.toPrettyString());
                                            return Mono.empty();
                                        }

                                        Long messageId = resultNode.path("message_id").asLong(0);
                                        if (messageId != 0) {
                                            log.info("{}⏳ Scheduling warning message deletion (ID: {}) in {} seconds.", logPrefix, messageId, DELETE_DELAY_SECONDS);

                                            // 调度删除任务
                                            // 修复：对非交互式警告消息，将 userId 设为 0L，防止 InteractiveMessageService 意外地清除用户的活跃会话。
                                            return interactiveMessageService.scheduleMessageDeletion(
                                                            token, 0L, chatId, messageId,
                                                            DELETE_DELAY_SECONDS, context.logIdentifier(), contextView
                                                    )
                                                    .onErrorResume(e -> {
                                                        log.warn("{}⚠️ Failed to schedule warning message deletion: {}", logPrefix, e.getMessage());
                                                        return Mono.empty(); // 调度失败不影响主流程
                                                    });
                                        }
                                    } catch (Exception e) {
                                        // 捕获 Jackson 转换错误
                                        log.error("{}❌ JSON/Object conversion error for warning message: {}", logPrefix, responseJson, e);
                                    }
                                    return Mono.empty(); // 如果解析或消息 ID 缺失，返回 Mono.empty()
                                })
                                .then(); // 转换为 Mono<Void>

                    } else {
                        // 用户没有会话标记，创建新会话
                        log.info("{}✅ User {} has no session markers. Creating new menu.", logPrefix, userId);
                        return processStartCommand(context, logPrefix, contextView);
                    }
                });
    }

    /**
     * 处理/start命令的实际逻辑
     */
    private Mono<Void> processStartCommand(HandlerContext context, String logPrefix, ContextView contextView) {
        String token = context.token();
        Long chatId = context.chatId();
        Long userId = context.userId();

        // 先设置正在处理/start的状态，防止重复点击
        return userSessionService.updateUserSession(userId, TelegramConstants.SESSION_STATE_PROCESSING_START, null)
                .then(Mono.defer(() -> {
                    return botMenuService.findMainMenuByBotName(context.botName(), 1)
                            .switchIfEmpty(Mono.fromCallable(() -> MenuType.createFallbackKeyboard(context.botEntity().getBotType().name())))
                            .filter(markup -> markup != null)
                            .flatMap(mainMenuMarkup ->
                                    botClientService.sendMenuMessageWithResponse(token, chatId, WELCOME_TEXT, mainMenuMarkup, context.chatTitle())
                            )
                            .doOnNext(responseJson -> handleSendResponse(responseJson, token, userId, chatId, logPrefix, contextView, context))
                            .doOnError(e -> {
                                log.error("{}❌ Failed to send initial menu message.", logPrefix, e);
                                userSessionService.clearUserSession(userId).contextWrite(reactor.util.context.Context.of(contextView)).subscribe();
                            })
                            .then();
                }));
    }

    /**
     * 💡 额外建议：将解析 Response 的逻辑也提取为私有方法，让 handleUpdate 更清晰
     */
    private void handleSendResponse(String responseJson, String token, Long userId, Long chatId, String logPrefix, ContextView contextView, HandlerContext context) {
        log.debug("{}🔍 Received Telegram sendMessage response JSON: {}", logPrefix, responseJson);
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode resultNode = root.path("result");

            if (!root.path("ok").asBoolean() || resultNode.isMissingNode()) {
                log.error("{}❌ API failure. JSON: {}", logPrefix, responseJson);
                return;
            }

            Long messageId = resultNode.path("message_id").asLong(0);
            if (messageId != 0) {
                // 📝 这里的 deletion 针对的是主菜单/交互式消息，应该保留 userId，确保 session 清理
                interactiveMessageService.scheduleMessageDeletion(
                        token, userId, chatId, messageId,
                        DELETE_DELAY_SECONDS, context.logIdentifier(), contextView
                ).subscribe();
            } else {
                log.warn("{}⚠️ Message ID is 0.", logPrefix);
            }
        } catch (Exception e) {
            log.error("{}❌ JSON parse error: {}", logPrefix, responseJson, e);
        }
    }
}