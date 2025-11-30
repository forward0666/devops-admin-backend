package com.backend.bot.controller;

import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.service.BotCoreService;
import com.backend.bot.service.BotUpdateHandlerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@Slf4j
public class BotWebhookController {

    private final BotCoreService botCoreService;
    private final BotUpdateHandlerService botUpdateHandlerService;

    // 辅助方法：尝试从 BotUpdateDto 中提取 chatId (保留在此 Controller 或移到工具类)
    private Optional<Long> extractChatId(BotUpdateDto update) {
        if (update.message() != null && update.message().chat() != null) {
            return Optional.ofNullable(update.message().chat().id());
        }
        if (update.callbackQuery() != null &&
                update.callbackQuery().message() != null &&
                update.callbackQuery().message().chat() != null) {
            return Optional.ofNullable(update.callbackQuery().message().chat().id());
        }
        return Optional.empty();
    }

    /**
     * 接收 Telegram Webhook 更新的端点。
     * 必须快速返回 HTTP 200 OK (Mono<Void>)。
     */
    @PostMapping("/callback/{botName}")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Void> onUpdateReceived(
            @PathVariable String botName,
            @RequestBody BotUpdateDto botUpdate) {

        // --- 提取并记录 chatId ---
        Optional<Long> chatIdOpt = extractChatId(botUpdate);
        String chatIdLog = chatIdOpt.map(id -> " (Chat ID: " + id + ")").orElse("");
        log.info("✅ START processing webhook for bot: {}{}", botName, chatIdLog);
        // -------------------------

        return Mono.just(botUpdate)
                .doOnNext(update -> log.info("✅ STAGE 1: Webhook body received, looking up BotEntity."))
                .flatMap(update ->
                        // 1. 查找 Bot 实体并设置超时
                        botCoreService.findByBotName(botName)
                                .timeout(Duration.ofSeconds(1), Mono.empty())
                                .onErrorResume(java.util.concurrent.TimeoutException.class, e -> {
                                    log.warn("⚠️ BotEntity lookup timed out (1s) during webhook processing for bot: {}", botName);
                                    return Mono.empty();
                                })
                )
                .filter(botEntity -> {
                    // 2. 校验 Bot 状态
                    if (botEntity.getStatus() == null || botEntity.getStatus() != 1) {
                        log.warn("⚠️ Webhook received update for inactive or unknown bot: {}", botName);
                        return false; // 状态不活跃则过滤掉
                    }
                    return true;
                })
                .doOnNext(entity -> log.info("STAGE 2: BotEntity found, executing business logic."))
                .flatMap(botEntity ->
                        // 3. 核心：将业务逻辑委派给 BotUpdateHandlerService
                        botUpdateHandlerService.handleUpdate(botEntity, botUpdate)
                )
                // 确保主 Webhook 链立即返回 Mono<Void>
                .doFinally(signalType -> {
                    log.info("✅ END processing webhook for bot: {}{} with signal: {}", botName, chatIdLog, signalType);
                })
                .onErrorResume(e -> {
                    log.error("❌ Error processing callback for bot {}", botName, e);
                    return Mono.empty();
                })
                .then(); // 确保返回 Mono<Void>
    }
}