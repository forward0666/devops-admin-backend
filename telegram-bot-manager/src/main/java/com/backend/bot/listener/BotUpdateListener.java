package com.backend.bot.listener;

import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.event.BotUpdateEvent;
import com.backend.bot.service.BotCoreService;
import com.backend.bot.service.BotUpdateService;
import filter.TraceIdFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.time.Duration;

import static com.backend.bot.util.BotChatUtils.extractChatId;

@Component
@Slf4j
public class BotUpdateListener {

    private final BotCoreService botCoreService;
    private final BotUpdateService botUpdateHandlerService;
    private final Scheduler blockingTaskScheduler;

    public BotUpdateListener(
            BotCoreService botCoreService,
            BotUpdateService botUpdateHandlerService,
            @Qualifier("blockingTaskScheduler") Scheduler blockingTaskScheduler) {
        this.botCoreService = botCoreService;
        this.botUpdateHandlerService = botUpdateHandlerService;
        this.blockingTaskScheduler = blockingTaskScheduler;
    }

    /**
     * 监听 BotUpdateEvent 事件。
     */
    @EventListener
    public void handleBotUpdateEvent(BotUpdateEvent event) {
        String botName = event.botName();
        BotUpdateDto botUpdate = event.botUpdate();
        final String traceId = event.traceId(); // 使用 final 捕获 Trace ID

        // 提取 Chat ID...
        Long chatId = extractChatId(botUpdate).orElse(null);

        // 1. 从 Mono.defer 开始
        Mono<Void> processingPipeline = Mono.defer(() -> {
                    // 2. 在 Mono 链中打印日志 (这里仍然使用事件捕获的 traceId，因为它独立于 MDC)
                    log.info("[traceId={}]📨 [AsyncListener] Processing event for bot: {} (ChatID: {})", traceId, botName, chatId);

                    return botCoreService.findByBotName(botName) // 🌟 重新从查找 Bot 实体开始
                            .timeout(Duration.ofSeconds(2), Mono.empty())
                            .onErrorResume(java.util.concurrent.TimeoutException.class, e -> {
                                log.warn("⚠️ BotConfigEntity lookup timed out for bot: {}", botName);
                                return Mono.empty();
                            });
                })
                // 3. 校验 Bot 状态
                .filter(botConfigEntity -> {
                    if (botConfigEntity.getStatus() == null || botConfigEntity.getStatus() != 1) {
                        log.warn("⏸️ Bot {} is inactive. Ignoring update.", botName);
                        return false;
                    }
                    return true;
                })
                // 4. 异步校验 Chat ID 白名单
                .flatMap(botConfigEntity -> {
                    if (chatId == null) {
                        log.debug("⚠️ No Chat ID found, skipping whitelist check for bot {}", botName);
                        return Mono.just(botConfigEntity);
                    }

                    return botCoreService.isChatIdAuthorized(botConfigEntity.getId(), botConfigEntity.getBotName(), chatId)
                            .flatMap(isAllowed -> {
                                if (Boolean.TRUE.equals(isAllowed)) {
                                    return Mono.just(botConfigEntity);
                                } else {
                                    log.warn("⛔ Rejected update for bot {} from UNAUTHORIZED Chat ID: {}", botName, chatId);
                                    return Mono.empty();
                                }
                            });
                })
                // 5. 核心：执行业务逻辑
                .flatMap(botConfigEntity -> {
                    // 确认 Update 成功通过所有前置校验，进入 handler service
                    String logMessage = "";
                    if (botUpdate.message() != null && botUpdate.message().text() != null) {
                        logMessage = String.format("Routing TEXT message (Length: %d)", botUpdate.message().text().length());
                    } else if (botUpdate.callbackQuery() != null) {
                        logMessage = "Routing CALLBACK query";
                    } else {
                        logMessage = "Routing OTHER update type";
                    }

                    // 修复：这里仍然使用事件捕获的 traceId
                    log.info("[traceId={}]✅ Update passed filters. {} to BotUpdateHandlerService.",
                            traceId, logMessage);

                    return botUpdateHandlerService.handleUpdate(botConfigEntity, botUpdate);
                })
                // 🌟 关键修正：将 contextWrite 放在 .then() 之前
                // 这样才能将 traceId 传播给下游的 StartCommandHandler
                .contextWrite(context -> {
                    if (traceId != null) {
                        // 将 Trace ID 写入 Reactor Context
                        return context.put(TraceIdFilter.CONTEXT_KEY_TRACE_ID, traceId);
                    }
                    return context;
                })
                // 6. 错误处理
                .doOnError(e -> log.error("❌ Error in async listener for bot {}", botName, e))
                .onErrorResume(e -> Mono.empty())
                .then(); // 转换为 Mono<Void>

        // 7. 手动订阅以触发执行 (Fire-and-Forget)
        processingPipeline
                .subscribeOn(blockingTaskScheduler)
                .subscribe();
    }
}