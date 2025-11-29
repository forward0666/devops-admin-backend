package com.backend.bot.controller;

import com.backend.bot.dto.BotRegisterDto;
import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.dto.SetWebhookDto;
import com.backend.bot.service.BotClientService;
import com.backend.bot.service.BotCoreService;
import com.backend.bot.vo.BotVo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import network.HttpResponseUtils; // 导入我们统一的响应工具类
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration; // 引入 Duration
import java.util.HashMap;
import java.util.Map;


@RestController
@Slf4j
@RequiredArgsConstructor
public class BotController {

    private final BotCoreService botCoreService;
    private final BotClientService botClientService;

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

    /**
     * Webhook 回调处理方法
     * 关键诊断日志已添加在 STAGE 1, 2, 3，用于精确诊断延迟。
     */
    @PostMapping("/callback/{botName}")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Void> onUpdateReceived(
            @PathVariable String botName,
            @RequestBody BotUpdateDto botUpdate) {

        log.info("✅ START processing webhook for bot: {}", botName);

        return Mono.just(botUpdate)
                .doOnNext(update -> log.info("✅ STAGE 1: Webhook body received, looking up BotEntity."))
                .flatMap(update ->
                        // ❗ 重点修复：对潜在慢速的 findByBotName (R2DBC/Redis) 添加 1 秒硬性超时。
                        botCoreService.findByBotName(botName)
                                .timeout(Duration.ofSeconds(1), Mono.empty()) // 如果 1 秒内未找到，则快速返回 Mono.empty()
                                .onErrorResume(java.util.concurrent.TimeoutException.class, e -> {
                                    log.warn("⚠️ BotEntity lookup timed out (1s) during webhook processing for bot: {}", botName);
                                    return Mono.empty();
                                })
                )
                .doOnNext(entity -> log.info("STAGE 2: BotEntity found, executing business logic."))
                .flatMap(botEntity -> {
                    // 校验逻辑
                    if (botEntity.getStatus() == null || botEntity.getStatus() != 1) {
                        log.warn("⚠️ Webhook received update for inactive or unknown bot: {}", botName);
                        return Mono.empty();
                    }

                    if (botUpdate.message() == null || botUpdate.message().text() == null) {
                        return Mono.empty();
                    }

                    String text = botUpdate.message().text();
                    Long chatId = botUpdate.message().chat().id();
                    String type = botUpdate.message().chat().type();
                    String token = botEntity.getBotToken();
                    String botUsername = botEntity.getBotUsername();
                    String logIdentifier = String.format("[%s/%s]", botName, botUsername);

                    log.info("✅ {} Received message in {}: {} ", logIdentifier, type, text);

                    String responseText;
                    if ("private".equals(type)) {
                        responseText = "Bot Manager Received message: " + text;
                    } else if (text.startsWith("/status")) {
                        responseText = "Bot Status: Active (Name: " + botName + ")";
                    } else if (text.contains("你好")) {
                        responseText = "大家好，我是由 Bot Manager 管理的机器人！";
                    } else {
                        return Mono.empty();
                    }

                    log.info("✅ {} STAGE 3: Preparing to send response message. Initiating Telegram API call (Detached).", logIdentifier);

                    // 异步触发，不等待结果
                    botClientService.sendMessage(token, chatId, responseText)
                            .subscribe();

                    // 确保主 Webhook 链立即返回 Mono<Void>
                    return Mono.empty();
                })
                // 移除外层 5 秒超时，专注于内层查找的快速失败
                .doFinally(signalType -> {
                    log.info("✅ END processing webhook for bot: {} with signal: {}", botName, signalType);
                })
                .onErrorResume(e -> {
                    log.error("❌ Error processing callback for bot {}", botName, e);
                    return Mono.empty();
                })
                .then(); // 确保返回 Mono<Void>
    }
}