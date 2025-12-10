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

import java.time.Duration;
import org.slf4j.MDC;

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
        boolean isStartCommand = update.message() != null &&
                update.message().text() != null &&
                update.message().text().trim().startsWith("/start");

        if (isStartCommand) {
            // 此处为同步方法，Trace ID 依赖于上游线程，通常不会自动打印前缀。
            log.info("✅ StartCommandHandler accepted: /start command.");
        }
        return isStartCommand;
    }

    @Override
    public Mono<Void> handle(BotConfigEntity botEntity, BotUpdateDto botUpdate) {
        String token = botEntity.getBotToken();
        String botName = botEntity.getBotName();
        Long chatId = botUpdate.message().chat().id();
        Long userId = botUpdate.message().from().id();

        String chatTitle = botUpdate.message().chat().title();

        return Mono.deferContextual(contextView -> {
                    // 1. 关键修正：在执行业务逻辑前，将 Trace ID 从 Reactor Context 同步到 MDC
                    LogUtils.syncTraceIdToMDC(contextView);

                    // 2. 🌟 强制打印 Trace ID: 从 MDC 获取 Trace ID 并构建日志前缀
                    String traceId = MDC.get("traceId");
                    String logPrefix = LogUtils.buildTraceIdLogPrefix(traceId);

                    // 🌟 修正：将日志移入到 MDC 已设置的区域，并手动添加前缀
                    log.info("{}🚀 Handling /start command from userId: {}", logPrefix, userId);

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