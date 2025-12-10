package com.backend.bot.handler;

import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.dto.InlineKeyboardMarkupDto;
import com.backend.bot.entity.BotConfigEntity;
import com.backend.bot.service.BotClientService;
import com.backend.bot.service.InMemoryUserSessionService;
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
import org.slf4j.MDC;

import java.time.Duration;
import java.util.Optional;

import static com.backend.bot.util.BotUpdateUtils.extractChatId;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(1) // 优先级最高
public class StartCommandHandler implements UpdateHandler {

    private final BotClientService botClientService;
    private final InMemoryUserSessionService userSessionService;
    private final ObjectMapper objectMapper;

    // 菜单消息文本
    private static final String WELCOME_TEXT = "✨✨✨ 选择服务: 👇👇";
    private static final int DELETE_DELAY_SECONDS = 5;

    @Override
    public boolean support(BotUpdateDto update) {
        // support 是同步方法，不建议在此处依赖 MDC，以避免 Trace ID 错乱
        boolean isStartCommand = update.message() != null &&
                update.message().text() != null &&
                update.message().text().trim().startsWith("/start");

        return isStartCommand;
    }

    @Override
    public Mono<Void> handle(BotConfigEntity botEntity, BotUpdateDto botUpdate) {
        String token = botEntity.getBotToken();
        String botName = botEntity.getBotName();
        Long chatId = botUpdate.message().chat().id();
        Long userId = botUpdate.message().from().id();

        String chatTitle = botUpdate.message().chat().title();

        // 提取用户昵称
        String firstName = botUpdate.message().from().firstName();

        return Mono.deferContextual(contextView -> {
                    // 🌟 关键修正：使用新的工具方法，一步完成 MDC 同步和日志前缀获取
                    String logPrefix = LogUtils.prepareMdcAndGetPrefix(contextView);

                    // 1. 构建用户日志后缀 (用户名称)
                    // 如果 firstName 不为空，则显示 "(用户名称)"
                    String userLogSuffix = firstName != null && !firstName.isEmpty()
                            ? String.format(" (%s)", firstName)
                            : "";

                    // 2. 构建聊天日志后缀 (Chat ID: xxx(群组名))
                    // 使用提供的 chatTitle，如果为空则显示 "N/A"
                    String chatLogSuffix = String.format(",Chat ID: %s(%s)",
                            chatId,
                            chatTitle != null ? chatTitle : "N/A");


                    // 🚀 整合日志：在 Trace ID 确定后，记录 Handler 被接受和开始处理
                    log.info("{}✅ [Accepted] StartCommandHandler accepted and handling /start command from userId: {}{}{}",
                            logPrefix, userId, userLogSuffix, chatLogSuffix);

                    // 3. 生成主菜单键盘
                    InlineKeyboardMarkupDto mainMenuMarkup = MenuType.createMainMenu();

                    // 4. 发送主菜单消息，并获取 messageId
                    Mono<String> sendMenuResponseMono = botClientService.sendMenuMessageWithResponse(token, chatId, WELCOME_TEXT, mainMenuMarkup, chatTitle);

                    return sendMenuResponseMono
                            .doOnNext(responseJson -> {
                                // 此处的日志应能自动获取 MDC 中的 Trace ID (或者依赖上游手动设置的 logPrefix)
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
                                        log.info("{}⏳ Menu message sent with ID: {}. Scheduling auto-deletion in {}s.", logPrefix, messageId, DELETE_DELAY_SECONDS);

                                        // 5. 安排自动删除任务
                                        Disposable deletionTask = Mono.delay(Duration.ofSeconds(DELETE_DELAY_SECONDS))
                                                .flatMap(aLong -> {
                                                    // 这里的 log.warn 应该能够继承 MDC
                                                    log.warn("{}⏰ Auto-deleting menu message {} after {}s timeout.", logPrefix, messageId, DELETE_DELAY_SECONDS);

                                                    // 5.1 执行删除操作
                                                    return botClientService.deleteMessage(token, chatId, messageId)
                                                            // 5.2 清除存储的任务引用
                                                            .then(userSessionService.cancelPendingDeletion(userId));
                                                })
                                                .subscribeOn(Schedulers.parallel())
                                                .subscribe();

                                        // 6. 存储任务引用
                                        userSessionService.storePendingDeletion(userId, deletionTask).subscribe();
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