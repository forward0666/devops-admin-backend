package com.backend.bot.listener;

import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.entity.BotConfigEntity;
import com.backend.bot.event.BotUpdateEvent;
import com.backend.bot.service.BotCoreService;
import com.backend.bot.service.BotUpdateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Optional;

import static com.backend.bot.utils.BotUpdateUtils.extractChatId;

@Component
@RequiredArgsConstructor
@Slf4j
public class BotUpdateListener {

    private final BotCoreService botCoreService;
    private final BotUpdateService botUpdateHandlerService;

    /**
     * 监听 BotUpdateEvent 事件。
     * 这里的逻辑完全从 Controller 迁移而来。
     */
    @Async // 可选：结合 Spring 的 TaskExecutor 使用，或者依赖下方的 subscribeOn
    @EventListener
    public void handleBotUpdateEvent(BotUpdateEvent event) {
        String botName = event.botName();
        BotUpdateDto botUpdate = event.botUpdate();

        // 提取 Chat ID 用于日志和白名单校验
        Optional<Long> chatIdOpt = extractChatId(botUpdate);
        Long chatId = chatIdOpt.orElse(null);

        log.info("📨 [AsyncListener] Processing event for bot: {} (ChatID: {})", botName, chatId);

        // 构建响应式业务流
        Mono<Void> processingPipeline = Mono.just(botName)
                // 1. 查找 Bot 配置实体
                .flatMap(name -> botCoreService.findByBotName(name)
                        .timeout(Duration.ofSeconds(2), Mono.empty()) // 放宽一点超时时间，因为是后台处理
                        .onErrorResume(java.util.concurrent.TimeoutException.class, e -> {
                            log.warn("⚠️ BotConfigEntity lookup timed out for bot: {}", name);
                            return Mono.empty();
                        })
                )
                // 2. 校验 Bot 状态
                .filter(botConfigEntity -> {
                    if (botConfigEntity.getStatus() == null || botConfigEntity.getStatus() != 1) {
                        log.warn("⏸️ Bot {} is inactive. Ignoring update.", botName);
                        return false;
                    }
                    return true;
                })
                // 3. 异步校验 Chat ID 白名单
                .flatMap(botConfigEntity -> {
                    // 如果没有 ChatID (例如 InlineQuery)，直接放行或根据需求处理
                    if (chatId == null) {
                        log.debug("⚠️ No Chat ID found, skipping whitelist check for bot {}", botName);
                        return Mono.just(botConfigEntity);
                    }

                    return botCoreService.isChatIdAuthorized(botConfigEntity.getId(), botConfigEntity.getBotName(), chatId)
                            .flatMap(isAllowed -> {
                                if (Boolean.TRUE.equals(isAllowed)) {
                                    return Mono.just(botConfigEntity); // 授权成功
                                } else {
                                    log.warn("⛔ Rejected update for bot {} from UNAUTHORIZED Chat ID: {}", botName, chatId);
                                    return Mono.empty(); // 授权失败
                                }
                            });
                })
                // 4. 核心：执行业务逻辑
                .flatMap(botConfigEntity -> botUpdateHandlerService.handleUpdate(botConfigEntity, botUpdate))
                // 错误处理
                .doOnError(e -> log.error("❌ Error in async listener for bot {}", botName, e))
                .onErrorResume(e -> Mono.empty())
                .then();

        // 🔥 关键：手动订阅以触发执行 (Fire-and-Forget)
        // 使用 boundedElastic 线程池，避免阻塞事件分发线程
        processingPipeline
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }
}