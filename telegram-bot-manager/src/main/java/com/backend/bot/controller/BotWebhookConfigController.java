package com.backend.bot.controller;

import com.backend.bot.dto.SetWebhookDto;
import com.backend.bot.service.BotClientService;
import com.backend.bot.service.BotCoreService;
import com.backend.bot.util.LogUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import network.HttpResponseUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
public class BotWebhookConfigController {

    private final BotCoreService botCoreService;
    private final BotClientService botClientService;

    /**
     * 手动设置 Bot 的 Webhook URL。
     */
    @PostMapping("/setWebhook")
    public Mono<ResponseEntity<Map<String, Object>>> setBotWebhook(
            @Valid @RequestBody Mono<SetWebhookDto> dtoMono,
            ServerWebExchange exchange) {

        // 🌟 关键修正：使用 Mono.deferContextual 替代 LogUtils.setMdcFromContext()
        return Mono.deferContextual(contextView -> {
                    // 步骤 1: 将 Trace ID 从 Reactor Context 同步到 MDC
                    LogUtils.syncTraceIdToMDC(contextView);

                    return dtoMono
                            .doOnNext(dto -> log.info("✅ Setting webhook for botName: {} to URL: {}", dto.getBotName(), dto.getUrl()))
                            .flatMap(dto -> {
                                return botCoreService.findByBotName(dto.getBotName())
                                        .flatMap(botEntity -> {
                                            if (botEntity.getBotToken() == null) {
                                                return Mono.just(HttpResponseUtils.badRequest("❌ Bot Token 缺失"));
                                            }
                                            String token = botEntity.getBotToken();
                                            String url = dto.getUrl();
                                            // 用前端传的 secret 拼接 Gateway header 参数
                                            String gatewaySecret = dto.getSecretToken();
                                            if (gatewaySecret != null && !gatewaySecret.isBlank()) {
                                                String separator = url.contains("?") ? "&" : "?";
                                                url = url + separator + "header_X-Encrypted-Data=" + gatewaySecret;
                                            }
                                            String finalUrl = url;

                                            return botClientService.setWebhook(token, finalUrl, null)
                                                    .flatMap(resultJson -> {
                                                        boolean success = resultJson != null && resultJson.contains("\"ok\":true");
                                                        if (success) {
                                                            botEntity.setWebhookUrl(finalUrl);
                                                            return botCoreService.saveBot(botEntity).thenReturn(HttpResponseUtils.ok());
                                                        } else {
                                                            return Mono.just(HttpResponseUtils.internalError("❌ Webhook 设置失败，Telegram API 返回错误"));
                                                        }
                                                    })
                                                    .onErrorResume(e -> {
                                                        log.error("❌setWebhook API call failed for {}", dto.getBotName(), e);
                                                        return Mono.just(HttpResponseUtils.internalError("❌ Webhook 设置时发生内部错误"));
                                                    });
                                        })
                                        .switchIfEmpty(Mono.just(HttpResponseUtils.notFound("❌ Bot 不存在")));
                            });
                })
                // 步骤 2: 使用 LogUtils 抽象的 MDC 清理方法
                .doFinally(LogUtils::clearMDC);
    }

    /**
     * 重置待处理更新（清除 Telegram 请求队列）
     */
    @PostMapping("/resetPendingUpdates")
    public Mono<ResponseEntity<Map<String, Object>>> resetPendingUpdates(@RequestParam String botName) {
        return Mono.deferContextual(contextView -> {
            LogUtils.syncTraceIdToMDC(contextView);
            log.info("Resetting pending updates for botName: {}", botName);
            return botCoreService.findByBotName(botName)
                    .switchIfEmpty(Mono.error(new IllegalArgumentException("❌ Bot 不存在")))
                    .flatMap(botEntity -> {
                        if (botEntity.getBotToken() == null) {
                            return Mono.error(new IllegalArgumentException("❌ Bot Token 缺失"));
                        }
                        String token = botEntity.getBotToken();
                        return botClientService.getWebhookInfo(token)
                                .flatMap(info -> {
                                    if (!info.containsKey("ok") || !(Boolean) info.get("ok")) {
                                        return Mono.just(HttpResponseUtils.internalError("❌ 获取 Webhook 信息失败"));
                                    }
                                    Map<String, Object> result = (Map<String, Object>) info.get("result");
                                    String url = (String) result.get("url");
                                    if (url == null || url.isBlank()) {
                                        return Mono.just(HttpResponseUtils.ok("No webhook set"));
                                    }
                                    // 重新设置 webhook 会自动清空 pending updates
                                    return botClientService.setWebhook(token, url, null)
                                            .map(res -> {
                                                if (res != null && res.contains("\"ok\":true")) {
                                                    return HttpResponseUtils.ok();
                                                }
                                                return HttpResponseUtils.internalError("❌ 重置失败");
                                            });
                                });
                    })
                    .onErrorResume(IllegalArgumentException.class, e -> Mono.just(HttpResponseUtils.badRequest(e.getMessage())))
                    .onErrorResume(e -> {
                        log.error("❌ resetPendingUpdates failed for {}", botName, e);
                        return Mono.just(HttpResponseUtils.internalError("❌ 重置时发生错误"));
                    });
        }).doFinally(LogUtils::clearMDC);
    }

    /**
     * 查询 Bot 的 Webhook 状态。
     */
    @GetMapping("/getWebhookInfo")
    public Mono<ResponseEntity<Map<String, Object>>> getBotWebhookInfo(@RequestParam String botName) {

        // 🌟 关键修正：使用 Mono.deferContextual 替代 LogUtils.setMdcFromContext()
        return Mono.deferContextual(contextView -> {
                    // 步骤 1: 将 Trace ID 从 Reactor Context 同步到 MDC
                    LogUtils.syncTraceIdToMDC(contextView);

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
                })
                // 步骤 2: 使用 LogUtils 抽象的 MDC 清理方法
                .doFinally(LogUtils::clearMDC);
    }

    /**
     * 删除 Webhook
     */
    @DeleteMapping("/deleteWebhook")
    public Mono<ResponseEntity<Map<String, Object>>> deleteWebhook(@RequestParam String botName) {
        return Mono.deferContextual(contextView -> {
            LogUtils.syncTraceIdToMDC(contextView);
            log.info("Deleting webhook for botName: {}", botName);
            return botCoreService.findByBotName(botName)
                    .switchIfEmpty(Mono.error(new IllegalArgumentException("❌ Bot 不存在")))
                    .flatMap(botEntity -> {
                        if (botEntity.getBotToken() == null) {
                            return Mono.error(new IllegalArgumentException("❌ Bot Token 缺失"));
                        }
                        return botClientService.deleteWebhook(botEntity.getBotToken())
                                .doOnNext(res -> log.info("TG deleteWebhook response: {}", res))
                                .flatMap(res -> {
                                    if (res != null && res.contains("\"ok\":true")) {
                                        botEntity.setWebhookUrl(null);
                                        return botCoreService.saveBot(botEntity).thenReturn(HttpResponseUtils.ok());
                                    }
                                    return Mono.just(HttpResponseUtils.internalError("❌ 删除 Webhook 失败: " + res));
                                });
                    })
                    .onErrorResume(IllegalArgumentException.class, e -> Mono.just(HttpResponseUtils.badRequest(e.getMessage())))
                    .onErrorResume(e -> {
                        log.error("❌ deleteWebhook failed for {}", botName, e);
                        return Mono.just(HttpResponseUtils.internalError("❌ 删除时发生错误"));
                    });
        }).doFinally(LogUtils::clearMDC);
    }
}