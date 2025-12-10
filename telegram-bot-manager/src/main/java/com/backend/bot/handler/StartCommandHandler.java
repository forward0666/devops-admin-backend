package com.backend.bot.handler;

import com.backend.bot.context.HandlerContext;
import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.dto.InlineKeyboardMarkupDto;
import com.backend.bot.service.BotClientService;
import com.backend.bot.service.InteractiveMessageService;
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
    private final ObjectMapper objectMapper;

    private static final String WELCOME_TEXT = "✨✨✨ 选择服务: 👇👇";
    private static final int DELETE_DELAY_SECONDS = 5;

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

        InlineKeyboardMarkupDto mainMenuMarkup = MenuType.createDynamicKeyboard("IP_WHITE_LIST");

        return botClientService.sendMenuMessageWithResponse(token, chatId, WELCOME_TEXT, mainMenuMarkup, context.chatTitle())
                .doOnNext(responseJson -> handleSendResponse(responseJson, token, userId, chatId, logPrefix, contextView))
                .onErrorResume(e -> {
                    log.error("{}❌ Failed to send initial menu message.", logPrefix, e);
                    return Mono.empty();
                })
                .then();
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