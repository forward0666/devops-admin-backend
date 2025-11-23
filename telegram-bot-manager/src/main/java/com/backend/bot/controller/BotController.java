package com.backend.bot.controller;

import com.backend.bot.dto.BotRegisterDto;
import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.dto.SetWebhookDto;
import com.backend.bot.entity.BotEntity;
import com.backend.bot.service.BotClientService; // 假设这些 Service 方法已返回 Mono/Flux
import com.backend.bot.service.BotCoreService;   // 假设这些 Service 方法已返回 Mono/Flux
import com.backend.bot.vo.BotVo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import network.HttpResponseUtils; // 导入我们统一的响应工具类
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange; // 引入 ServerWebExchange
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;


@RestController
@Slf4j
@RequiredArgsConstructor
public class BotController {

    private final BotCoreService botCoreService;
    private final BotClientService botClientService;

    /**
     * 【转换点 1】：返回 Mono<ResponseEntity<...>>
     * 【转换点 2】：移除手动 try-finally MDC.clear()
     */
    @PostMapping("/addBot")
    public Mono<ResponseEntity<Map<String, Object>>> addBot(
            @Valid @RequestBody Mono<BotRegisterDto> dtoMono) { // 接收 Mono<DTO>

        return dtoMono
                .doOnNext(dto -> log.info("Received request to register bot: {}", dto.getBotUsername()))
                // 确保 botCoreService.registerNewBot 返回 Mono<BotVo>
                .flatMap(botCoreService::registerNewBot)
                .map(botVo -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("bot", botVo);
                    // 使用 HttpResponseUtils 构建 201 Created 响应
                    return HttpResponseUtils.created("Bot 注册成功并已设置 Webhook");
                })
                .onErrorResume(e -> {
                    log.error("Bot registration failed", e);
                    // 统一异常处理，返回 500
                    return Mono.just(HttpResponseUtils.internalError("Bot 注册失败: " + e.getMessage()));
                });
    }

    /**
     * 【转换点 1】：返回 Mono<ResponseEntity<...>>
     * 【转换点 3】：使用 ServerWebExchange 替代 HttpServletRequest (若需要获取 IP/Header)
     */
    @PostMapping("/setWebhook")
    public Mono<ResponseEntity<Map<String, Object>>> setBotWebhook(
            @Valid @RequestBody Mono<SetWebhookDto> dtoMono,
            ServerWebExchange exchange) {

        return dtoMono.flatMap(dto -> {
            log.info("Setting webhook for botName: {} to URL: {}", dto.getBotName(), dto.getUrl());

            // 响应式查找 BotEntity (假设 findByBotName 返回 Mono<BotEntity>)
            return botCoreService.findByBotName(dto.getBotName())
                    .flatMap(botEntity -> {
                        if (botEntity.getBotToken() == null) {
                            return Mono.just(HttpResponseUtils.badRequest("Bot Token 缺失"));
                        }
                        String token = botEntity.getBotToken();
                        String url = dto.getUrl();
                        String secretToken = dto.getSecretToken();

                        // 响应式调用 BotClientService.setWebhook (假设返回 Mono<String> - 原始 JSON)
                        return botClientService.setWebhook(token, url, secretToken)
                                .map(resultJson -> {
                                    boolean success = resultJson != null && resultJson.contains("\"ok\":true");
                                    if (success) {
                                        return HttpResponseUtils.ok();
                                    } else {
                                        return HttpResponseUtils.internalError("Webhook 设置失败，Telegram API 返回错误");
                                    }
                                })
                                .onErrorResume(e -> {
                                    log.error("setWebhook API call failed for {}", dto.getBotName(), e);
                                    return Mono.just(HttpResponseUtils.internalError("Webhook 设置时发生内部错误"));
                                });
                    })
                    // 如果 botCoreService.findByBotName 返回 Mono.empty()
                    .switchIfEmpty(Mono.just(HttpResponseUtils.notFound("Bot 不存在")));
        });
    }

    /**
     * 【转换点 1】：返回 Mono<ResponseEntity<...>>
     */
    @GetMapping("/getWebhookInfo")
    public Mono<ResponseEntity<Map<String, Object>>> getBotWebhookInfo(@RequestParam String botName) {

        log.info("Getting webhook info for botName: {}", botName);

        // 响应式查找 BotEntity
        return botCoreService.findByBotName(botName)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Bot 不存在或 Token 缺失")))
                .flatMap(botEntity -> {
                    if (botEntity.getBotToken() == null) {
                        return Mono.error(new IllegalArgumentException("Bot Token 缺失"));
                    }
                    String token = botEntity.getBotToken();

                    // 响应式调用 BotClientService.getWebhookInfo (假设返回 Mono<Map<String, Object>>)
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
                    // 处理 Bot 不存在或 Token 缺失的自定义异常
                    return Mono.just(HttpResponseUtils.badRequest(e.getMessage()));
                })
                .onErrorResume(e -> {
                    log.error("getWebhookInfo failed for {}", botName, e);
                    return Mono.just(HttpResponseUtils.internalError("查询 Webhook 状态时发生内部错误"));
                });
    }

    /**
     * 【转换点 4】：返回 Mono<Void>
     * 【转换点 2】：移除 MDC.get() 和 MDC.clear()，直接从链中处理
     */
    @PostMapping("/callback/{botName}")
    @ResponseStatus(HttpStatus.OK) // 收到 Webhook 默认返回 200 OK
    public Mono<Void> onUpdateReceived(
            @PathVariable String botName,
            @RequestBody BotUpdateDto botUpdate) {

        // 使用 Mono.just(botUpdate) 开始响应式链
        return Mono.just(botUpdate)
                .flatMap(update -> botCoreService.findByBotName(botName)) // 响应式查找 BotEntity
                .flatMap(botEntity -> {
                    // 校验逻辑
                    if (botEntity.getStatus() == null || botEntity.getStatus() != 1) {
                        log.warn("Webhook received update for inactive or unknown bot: {}", botName);
                        return Mono.empty(); // 终止链并返回 Mono<Void>
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

                    // 提取 Trace ID 依赖于 WebFlux 过滤器的 Reactor Context
                    // 假设 MDC 已经设置在异步线程中 (参见 AuthFilter 的处理方式)

                    log.info("{} Received message in {}: {} ", logIdentifier, type, text);

                    String responseText;
                    if ("private".equals(type)) {
                        responseText = "Bot Manager Received message: " + text;
                    } else if (text.startsWith("/status")) {
                        responseText = "Bot Status: Active (Name: " + botName + ")";
                    } else if (text.contains("你好")) {
                        responseText = "大家好，我是由 Bot Manager 管理的机器人！";
                    } else {
                        return Mono.empty(); // 不回复，终止链
                    }

                    // 响应式发送消息 (假设 sendMessage 返回 Mono<Void>)
                    return botClientService.sendMessage(token, chatId, responseText);
                })
                .onErrorResume(e -> {
                    log.error("Error processing callback for bot {}", botName, e);
                    return Mono.empty(); // 发生错误，返回 Mono<Void> 确保 Webhook 返回 200 OK
                })
                .then(); // 确保最终返回 Mono<Void>
    }
}