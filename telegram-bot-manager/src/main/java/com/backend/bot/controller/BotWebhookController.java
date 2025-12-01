package com.backend.bot.controller;

import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.service.BotCoreService;
import com.backend.bot.service.BotUpdateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;

import static com.backend.bot.utils.BotUpdateUtils.extractChatId;

@RestController
@RequiredArgsConstructor
@Slf4j
public class BotWebhookController {

    private final BotCoreService botCoreService;
    private final BotUpdateService botUpdateHandlerService;

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

        // 如果无法提取 Chat ID (例如：某些特殊的 Inline Query)，则跳过白名单检查
        if (chatIdOpt.isEmpty()) {
            log.warn("⚠️ Cannot extract Chat ID from update. Skipping authorization check and proceeding with BotEntity lookup.");
            // 假设 findByBotName 返回 BotConfigEntity
            return botCoreService.findByBotName(botName)
                    .flatMap(botConfigEntity -> botUpdateHandlerService.handleUpdate(botConfigEntity, botUpdate))
                    .then()
                    .onErrorResume(e -> {
                        log.error("❌ Error processing callback (No Chat ID) for bot {}", botName, e);
                        return Mono.empty();
                    });
        }

        Long chatId = chatIdOpt.get(); // 确定 chatId 存在

        return Mono.just(botUpdate)
                .doOnNext(update -> log.info("✅ STAGE 1: Webhook body received, looking up BotConfigEntity."))
                .flatMap(update ->
                        // 1. 查找 Bot 配置实体并设置超时
                        botCoreService.findByBotName(botName)
                                .timeout(Duration.ofSeconds(1), Mono.empty())
                                .onErrorResume(java.util.concurrent.TimeoutException.class, e -> {
                                    log.warn("⚠️ BotConfigEntity lookup timed out (1s) during webhook processing for bot: {}", botName);
                                    return Mono.empty();
                                })
                )
                .filter(botConfigEntity -> {
                    // 2. 校验 Bot 状态
                    // 假设 BotConfigEntity 有 getStatus() 方法
                    if (botConfigEntity.getStatus() == null || botConfigEntity.getStatus() != 1) {
                        log.warn("⚠️ Webhook received update for inactive or unknown bot: {}", botName);
                        return false; // 状态不活跃则过滤掉
                    }
                    return true;
                })
                .flatMap(botConfigEntity ->
                        // 3. 异步校验 Chat ID 白名单 (新增核心逻辑)
                        // 假设 BotConfigEntity 有 getId() 方法
                        botCoreService.isChatIdAuthorized(botConfigEntity.getId(),botConfigEntity.getBotName(), chatId)
                                .flatMap(isAllowed -> {
                                    if (isAllowed) {
                                        log.info("STAGE 2: BotConfigEntity found, Chat ID authorized. Executing business logic.");
                                        return Mono.just(botConfigEntity); // 授权成功，继续传递实体
                                    } else {
                                        log.warn("❌ Rejected update for bot {} from UNAUTHORIZED Chat ID: {}", botName, chatId);
                                        return Mono.empty(); // 授权失败，终止链
                                    }
                                })
                )
                .flatMap(botConfigEntity ->
                        // 4. 核心：将业务逻辑委派给 BotUpdateHandlerService
                        botUpdateHandlerService.handleUpdate(botConfigEntity, botUpdate)
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