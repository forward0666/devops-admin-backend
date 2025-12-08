package com.backend.bot.controller;

import com.backend.bot.dto.SetWebhookDto;
import com.backend.bot.service.BotClientService;
import com.backend.bot.service.BotCoreService;
import filter.TraceIdFilter; // 🌟 导入 TraceIdFilter
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import network.HttpResponseUtils;
import org.slf4j.MDC; // 🌟 导入 MDC
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional; // 🌟 导入 Optional

@RestController
@RequiredArgsConstructor
@Slf4j
public class BotWebhookConfigController {

    private final BotCoreService botCoreService;
    private final BotClientService botClientService;

    // 辅助方法：获取 Trace ID 并设置 MDC
    private Mono<Void> setMdcFromContext() {
        return Mono.deferContextual(contextView -> {
            Optional<Object> traceIdOpt = contextView.getOrEmpty(TraceIdFilter.CONTEXT_KEY_TRACE_ID);
            if (traceIdOpt.isPresent()) {
                String traceId = traceIdOpt.get().toString();
                MDC.put("traceId", traceId);
            }
            return Mono.empty();
        });
    }

    /**
     * 手动设置 Bot 的 Webhook URL。
     */
    @PostMapping("/setWebhook")
    public Mono<ResponseEntity<Map<String, Object>>> setBotWebhook(
            @Valid @RequestBody Mono<SetWebhookDto> dtoMono,
            ServerWebExchange exchange) {

        return setMdcFromContext() // 🌟 步骤 1: 设置 MDC
                .then(dtoMono)
                .doOnNext(dto -> log.info("✅ Setting webhook for botName: {} to URL: {}", dto.getBotName(), dto.getUrl()))
                .flatMap(dto -> {
                    // ... (原有逻辑)
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
                })
                .doFinally(signal -> MDC.clear()); // 🌟 步骤 2: 在链结束时清除 MDC
    }

    /**
     * 查询 Bot 的 Webhook 状态。
     */
    @GetMapping("/getWebhookInfo")
    public Mono<ResponseEntity<Map<String, Object>>> getBotWebhookInfo(@RequestParam String botName) {

        return setMdcFromContext() // 🌟 步骤 1: 设置 MDC
                .then(Mono.defer(() -> {
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
                }))
                .doFinally(signal -> MDC.clear()); // 🌟 步骤 2: 在链结束时清除 MDC
    }
}