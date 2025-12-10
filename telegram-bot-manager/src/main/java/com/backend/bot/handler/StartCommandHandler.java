package com.backend.bot.handler;

import com.backend.bot.constants.TelegramConstants;
import com.backend.bot.context.HandlerContext;
import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.dto.InlineKeyboardMarkupDto;
import com.backend.bot.entity.UserSessionEntity;
import com.backend.bot.service.BotClientService;
import com.backend.bot.service.InteractiveMessageService;
import com.backend.bot.service.UserSessionService;
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
    private final ObjectMapper objectMapper;

    private static final String WELCOME_TEXT = TelegramConstants.WELCOME_MESSAGE;
    private static final int DELETE_DELAY_SECONDS = TelegramConstants.DEFAULT_DELETE_DELAY_SECONDS;

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

        // 检查用户是否已经在处理/start请求
        return userSessionService.getUserSession(userId)
                .flatMap(existingSession -> {
                    // 如果用户正在处理/start请求，返回提示信息
                    if (TelegramConstants.SESSION_STATE_PROCESSING_START.equals(existingSession.getState())) {
                        log.info("{}⚠️ User {} is already processing /start command. Ignoring duplicate request.", logPrefix, userId);
                        return botClientService.sendMessage(token, chatId, "⏳ 正在处理您的请求，请稍候...", null)
                                .then(Mono.empty());
                    }
                    
                    // 用户有其他会话，取消现有会话并继续处理/start
                    return userSessionService.clearUserSession(userId)
                            .then(processStartCommand(context, logPrefix, contextView));
                })
                // 如果用户没有会话，直接处理/start
                .switchIfEmpty(processStartCommand(context, logPrefix, contextView));
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
                    InlineKeyboardMarkupDto mainMenuMarkup = MenuType.createDynamicKeyboard("IP_WHITE_LIST");

                    return botClientService.sendMenuMessageWithResponse(token, chatId, WELCOME_TEXT, mainMenuMarkup, context.chatTitle())
                            .doOnNext(responseJson -> handleSendResponse(responseJson, token, userId, chatId, logPrefix, contextView))
                            .doOnSuccess(responseJson -> {
                                // 成功发送消息后，清除处理状态，允许用户再次点击/start
                                userSessionService.clearUserSession(userId).subscribe();
                            })
                            .onErrorResume(e -> {
                                log.error("{}❌ Failed to send initial menu message.", logPrefix, e);
                                // 出错时也要清除处理状态
                                userSessionService.clearUserSession(userId).subscribe();
                                return Mono.empty();
                            });
                }));
    }

    /**
     * 💡 额外建议：将解析 Response 的逻辑也提取为私有方法，让 handleUpdate 更清晰
     */
    private void handleSendResponse(String responseJson, String token, Long userId, Long chatId, String logPrefix, ContextView contextView) {
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
                interactiveMessageService.scheduleMessageDeletion(
                        token, userId, chatId, messageId,
                        DELETE_DELAY_SECONDS, logPrefix, contextView
                ).subscribe();
            } else {
                log.warn("{}⚠️ Message ID is 0.", logPrefix);
            }
        } catch (Exception e) {
            log.error("{}❌ JSON parse error: {}", logPrefix, responseJson, e);
        }
    }
}