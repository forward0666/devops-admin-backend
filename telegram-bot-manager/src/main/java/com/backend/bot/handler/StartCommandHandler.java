package com.backend.bot.handler;

import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.dto.InlineKeyboardMarkupDto;
import com.backend.bot.entity.BotConfigEntity;
import com.backend.bot.service.BotClientService;
import com.backend.bot.service.InMemoryUserSessionService;
import com.backend.bot.service.InteractiveMessageService; // 引入新的服务
import com.backend.bot.template.MenuType;
import com.backend.bot.util.LogUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.time.Duration;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(1) // 优先级最高
public class StartCommandHandler implements UpdateHandler {

    private final BotClientService botClientService;
    private final InMemoryUserSessionService userSessionService;
    private final InteractiveMessageService interactiveMessageService; // 注入新的服务
    private final ObjectMapper objectMapper;

    // 菜单消息文本
    private static final String WELCOME_TEXT = "✨✨✨ 选择服务: 👇👇";
    private static final int DELETE_DELAY_SECONDS = 5;

    @Override
    public boolean support(BotUpdateDto update) {
        boolean isStartCommand = update.message() != null &&
                update.message().text() != null &&
                update.message().text().trim().startsWith("/start");

        return isStartCommand;
    }

    @Override
    public Mono<Void> handle(BotConfigEntity botEntity, BotUpdateDto botUpdate) {
        String token = botEntity.getBotToken();
        Long chatId = botUpdate.message().chat().id();
        Long userId = botUpdate.message().from().id();

        String chatTitle = botUpdate.message().chat().title();
        String firstName = botUpdate.message().from().firstName();

        return Mono.deferContextual(contextView -> {
                    // 1. 捕获原始上下文 (包含 traceId)，传递给 service
                    final ContextView finalContext = contextView;

                    // 2. 准备 MDC 和日志前缀
                    String logPrefix = LogUtils.prepareMdcAndGetPrefix(contextView);

                    // 3. 构建用户日志后缀
                    String userLogSuffix = firstName != null && !firstName.isEmpty()
                            ? String.format(" (%s)", firstName)
                            : "";
                    // 4. 构建聊天日志后缀
                    String chatLogSuffix = String.format(",Chat ID: %s(%s)",
                            chatId,
                            chatTitle != null ? chatTitle : "N/A");

                    log.info("{}✅ [Accepted] StartCommandHandler accepted and handling /start command from userId: {}{}{}",
                            logPrefix, userId, userLogSuffix, chatLogSuffix);

                    // 5. 生成主菜单键盘
                    InlineKeyboardMarkupDto mainMenuMarkup = MenuType.createMainMenu();

                    // 6. 发送主菜单消息，并获取 messageId
                    Mono<String> sendMenuResponseMono = botClientService.sendMenuMessageWithResponse(token, chatId, WELCOME_TEXT, mainMenuMarkup, chatTitle);

                    return sendMenuResponseMono
                            .doOnNext(responseJson -> {
                                log.debug("{}🔍 Received Telegram sendMessage response JSON: {}", logPrefix, responseJson);

                                try {
                                    // 尝试解析JSON
                                    JsonNode root = objectMapper.readTree(responseJson);
                                    JsonNode resultNode = root.path("result");
                                    boolean isOk = root.path("ok").asBoolean();

                                    if (!isOk || resultNode.isMissingNode()) {
                                        log.error("{}❌ Telegram API returned failure (ok=false) or missing 'result' node in response. JSON: {}", logPrefix, responseJson);
                                        return;
                                    }

                                    Long messageId = resultNode.path("message_id").asLong();

                                    if (messageId != 0) {

                                        // ⭐ 关键修改：调用新的服务方法，并将 ContextView 传递过去
                                        interactiveMessageService.scheduleMessageDeletion(
                                                token,
                                                userId,
                                                chatId,
                                                messageId,
                                                DELETE_DELAY_SECONDS,
                                                logPrefix, // 传递 Trace ID 前缀用于日志
                                                finalContext // 传递捕获到的上下文
                                        ).subscribe();
                                        // 日志已经在 service 内部记录，这里不需要重复记录
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
                            });
                })
                .doOnError(e -> log.error("❌ Unhandled error in StartCommandHandler pipeline.", e))
                .then()
                // 确保在流结束时清理 MDC
                .doFinally(LogUtils::clearMDC);
    }
}