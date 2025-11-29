package com.backend.bot.controller;

import com.backend.bot.dto.BotRegisterDto;
import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.dto.SetWebhookDto;
import com.backend.bot.service.BotClientService;
import com.backend.bot.service.BotCoreService;
import com.backend.bot.service.BotUpdateHandlerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import network.HttpResponseUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional; // 确保引入

@RestController
@RequiredArgsConstructor
@Slf4j
public class BotController {

    private final BotCoreService botCoreService;
    private final BotClientService botClientService;
    private final BotUpdateHandlerService botUpdateHandlerService;

    // 辅助方法：尝试从 BotUpdateDto 中提取 chatId
    // 使用 Record 的访问器：fieldName() 而不是 getFieldName()
    private Optional<Long> extractChatId(BotUpdateDto update) {
        // 1. 尝试从 Message 中获取 chatId
        if (update.message() != null && update.message().chat() != null) {
            return Optional.ofNullable(update.message().chat().id());
        }
        // 2. 尝试从 CallbackQuery 中获取 chatId
        if (update.callbackQuery() != null &&
                update.callbackQuery().message() != null &&
                update.callbackQuery().message().chat() != null) {

            return Optional.ofNullable(update.callbackQuery().message().chat().id());
        }
        // 3. 尝试从其他更新类型中获取（如 Channel Post 等）
        return Optional.empty();
    }

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

    @PostMapping("/addBot")
    public Mono<ResponseEntity<Map<String, Object>>> addBot(
            @Valid @RequestBody Mono<BotRegisterDto> dtoMono) {

        return dtoMono
                .doOnNext(dto -> log.info("✅ Received request to register bot: {}", dto.getBotUsername()))
                .flatMap(botCoreService::registerNewBot)
                .map(botVo -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("bot", botVo);
                    return HttpResponseUtils.created("✅ Bot 注册成功并已设置 Webhook");
                })
                .onErrorResume(e -> {
                    log.error("❌ Bot registration failed", e);
                    return Mono.just(HttpResponseUtils.internalError("❌ Bot 注册失败: " + e.getMessage()));
                });
    }

    @PostMapping("/setWebhook")
    public Mono<ResponseEntity<Map<String, Object>>> setBotWebhook(
            @Valid @RequestBody Mono<SetWebhookDto> dtoMono,
            ServerWebExchange exchange) {

        return dtoMono.flatMap(dto -> {
            log.info("✅ Setting webhook for botName: {} to URL: {}", dto.getBotName(), dto.getUrl());

            return botCoreService.findByBotName(dto.getBotName())
                    .flatMap(botEntity -> {
                        if (botEntity.getBotToken() == null) {
                            return Mono.just(HttpResponseUtils.badRequest("❌ Bot Token 缺失"));
                        }
                        String token = botEntity.getBotToken();
                        String url = dto.getUrl();
                        String secretToken = dto.getSecretToken();

                        return botClientService.setWebhook(token, url, secretToken)
                                .map(resultJson -> {
                                    boolean success = resultJson != null && resultJson.contains("\"ok\":true");
                                    if (success) {
                                        return HttpResponseUtils.ok();
                                    } else {
                                        return HttpResponseUtils.internalError("❌ Webhook 设置失败，Telegram API 返回错误");
                                    }
                                })
                                .onErrorResume(e -> {
                                    log.error("❌setWebhook API call failed for {}", dto.getBotName(), e);
                                    return Mono.just(HttpResponseUtils.internalError("❌ Webhook 设置时发生内部错误"));
                                });
                    })
                    .switchIfEmpty(Mono.just(HttpResponseUtils.notFound("❌ Bot 不存在")));
        });
    }

    @GetMapping("/getWebhookInfo")
    public Mono<ResponseEntity<Map<String, Object>>> getBotWebhookInfo(@RequestParam String botName) {

        log.info("Getting webhook info for botName: {}", botName);

        return botCoreService.findByBotName(botName)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("❌ Bot 不存在或 Token 缺失")))
                .flatMap(botEntity -> {
                    if (botEntity.getBotToken() == null) {
                        return Mono.error(new IllegalArgumentException("❌Bot Token 缺失"));
                    }
                    String token = botEntity.getBotToken();

                    return botClientService.getWebhookInfo(token)
                            .map(info -> {
                                if (info.containsKey("ok") && (Boolean) info.get("ok")) {
                                    Map<String, Object> data = new HashMap<>();
                                    data.put("webhookInfo", info.get("result"));
                                    return HttpResponseUtils.ok(data);
                                } else {
                                    String errMsg = info.containsKey("description") ? (String) info.get("description") : "Webhook 状态查询失败";
                                    return HttpResponseUtils.internalError(errMsg);
                                }
                            });
                })
                .onErrorResume(IllegalArgumentException.class, e -> {
                    return Mono.just(HttpResponseUtils.badRequest(e.getMessage()));
                })
                .onErrorResume(e -> {
                    log.error("❌getWebhookInfo failed for {}", botName, e);
                    return Mono.just(HttpResponseUtils.internalError("❌ 查询 Webhook 状态时发生内部错误"));
                });
    }
}