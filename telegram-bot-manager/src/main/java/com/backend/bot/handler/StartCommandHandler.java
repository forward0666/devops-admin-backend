package com.backend.bot.handler;

import com.backend.bot.context.HandlerContext;
import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.dto.InlineKeyboardMarkupDto;
import com.backend.bot.service.BotClientService;
import com.backend.bot.service.InteractiveMessageService;
import com.backend.bot.template.MenuType;
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
@Order(1) // 优先级最高
public class StartCommandHandler extends AbstractUpdateHandler {

    private final BotClientService botClientService;
    private final InteractiveMessageService interactiveMessageService;
    private final ObjectMapper objectMapper;

    // 菜单消息文本
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
        String chatTitle = context.chatTitle();
        String firstName = context.firstName();

        String userLogSuffix = firstName != null && !firstName.isEmpty()
                ? String.format(" (%s)", firstName)
                : "";
        String chatLogSuffix = String.format(",Chat ID: %s(%s)",
                chatId,
                chatTitle != null ? chatTitle : "N/A");

        log.info("{}✅ Handling /start command from userId: {}{}{}",
                logPrefix, userId, userLogSuffix, chatLogSuffix);

        InlineKeyboardMarkupDto mainMenuMarkup = MenuType.createMainMenu();

        Mono<String> sendMenuResponseMono = botClientService.sendMenuMessageWithResponse(token, chatId, WELCOME_TEXT, mainMenuMarkup, chatTitle);

        return sendMenuResponseMono
                .doOnNext(responseJson -> {
                    log.debug("{}🔍 Received Telegram sendMessage response JSON: {}", logPrefix, responseJson);

                    try {
                        JsonNode root = objectMapper.readTree(responseJson);
                        JsonNode resultNode = root.path("result");
                        boolean isOk = root.path("ok").asBoolean();

                        if (!isOk || resultNode.isMissingNode()) {
                            log.error("{}❌ Telegram API returned failure (ok=false) or missing 'result' node in response. JSON: {}", logPrefix, responseJson);
                            return;
                        }

                        Long messageId = resultNode.path("message_id").asLong();

                        if (messageId != 0) {
                            interactiveMessageService.scheduleMessageDeletion(
                                    token,
                                    userId,
                                    chatId,
                                    messageId,
                                    DELETE_DELAY_SECONDS,
                                    logPrefix,
                                    contextView
                            ).subscribe();
                        } else {
                            log.warn("{}⚠️ Message ID extraction failed (messageId=0) or message was not sent correctly.", logPrefix);
                        }
                    } catch (Exception e) {
                        log.error("{}❌ Failed to parse sendMessage response or schedule deletion. JSON: {}", logPrefix, responseJson, e);
                    }
                })
                .onErrorResume(e -> {
                    log.error("{}❌ Failed to send initial menu message.", logPrefix, e);
                    return Mono.empty();
                })
                .then();
    }
}