package com.backend.bot.listener;

import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.event.BotUpdateEvent;
import com.backend.bot.entity.BotConfigEntity;
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

import static com.backend.bot.util.BotUpdateUtils.extractChatId;

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
        String traceId = event.traceId();

        // 提取 Chat ID...
        // 假设 extractChatId 方法返回 Optional<Long>
        Long chatId = extractChatId(botUpdate).orElse(null);

        // 1. 从 Mono.defer 开始，将 Trace ID 写入 Context
        Mono<Void> processingPipeline = Mono.defer(() -> {
                    // 2. 在 Context 写入后，在 Mono 链中打印日志
                    log.info("{}📨 [AsyncListener] Processing event for bot: {} (ChatID: {})",traceId, botName, chatId);

                    return botCoreService.findByBotName(botName) // 🌟 重新从查找 Bot 实体开始
                            .timeout(Duration.ofSeconds(2), Mono.empty())
                            .onErrorResume(java.util.concurrent.TimeoutException.class, e -> {
                                log.warn("⚠️ BotConfigEntity lookup timed out for bot: {}", botName);
                                return Mono.empty();
                            });
                })
                // 3. 校验 Bot 状态 (类型已经是 Mono<BotConfigEntity>)
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
                    // 【新增调试日志】确认 Update 成功通过所有前置校验，进入 handler service
                    if (botUpdate.message() != null && botUpdate.message().text() != null) {
                        log.info("✅ Update passed filters. Routing TEXT message (Length: {}) to BotUpdateHandlerService.",
                                botUpdate.message().text().length());
                    } else if (botUpdate.callbackQuery() != null) {
                        log.info("✅ Update passed filters. Routing CALLBACK query to BotUpdateHandlerService.");
                    } else {
                        log.info("✅ Update passed filters. Routing OTHER update type to BotUpdateHandlerService.");
                    }

                    return botUpdateHandlerService.handleUpdate(botConfigEntity, botUpdate);
                })
                // 6. 错误处理
                .doOnError(e -> log.error("❌ Error in async listener for bot {}", botName, e))
                .onErrorResume(e -> Mono.empty())
                .then() // 转换为 Mono<Void>
                // 🌟 关键：将 Trace ID 写入 Context
                .contextWrite(context -> {
                    if (traceId != null) {
                        return context.put(TraceIdFilter.CONTEXT_KEY_TRACE_ID, traceId);
                    }
                    return context;
                });

        // 7. 手动订阅以触发执行 (Fire-and-Forget)
        processingPipeline
                .subscribeOn(blockingTaskScheduler)
                .subscribe();
    }
}