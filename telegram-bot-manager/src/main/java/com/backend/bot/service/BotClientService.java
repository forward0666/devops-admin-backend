package com.backend.bot.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters; // 导入 BodyInserters
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.PrematureCloseException;

import java.util.HashMap; // 导入 HashMap
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class BotClientService {

    // 注入 WebClient，通常在配置类中配置好 base url
    private final WebClient telegramWebClient;
    private final ObjectMapper objectMapper;

    @Value("${telegram.api-base-url:https://api.telegram.org}")
    private String telegramApiBaseUrl;

    @Value("${telegram.webhook-domain}")
    private String webhookDomain;

    /**
     * 注册/更新 Webhook URL。
     */
    public Mono<String> setWebhook(String token, String fullWebhookUrl, String secretToken) {

        String path = "/bot" + token + "/setWebhook";

        return telegramWebClient.get()
                .uri(path, uriBuilder -> uriBuilder
                        .queryParam("url", fullWebhookUrl)
                        .queryParam("secret_token", secretToken)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .onErrorResume(e -> {
                    log.error("❌Telegram setWebhook API call failed for token: {}", token, e);
                    // 返回一个包含错误信息的 Mono
                    return Mono.just("{\"ok\":false, \"description\":\"Telegram API error: " + e.getMessage() + "\"}");
                })
                .doOnSuccess(response -> log.info("setWebhook response received successfully."));
    }

    /**
     * 获取 Bot 的 Webhook 状态及 pending 消息数。
     */
    public Mono<Map<String, Object>> getWebhookInfo(String token) {
        String path = "/bot" + token + "/getWebhookInfo";

        return telegramWebClient.get()
                .uri(path)
                .retrieve()
                .bodyToMono(String.class)
                // 🟢 切换到 boundedElastic 线程池执行阻塞操作
                .publishOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .flatMap(jsonResponse -> {
                    try {
                        Map<String, Object> map = objectMapper.readValue(jsonResponse, new TypeReference<Map<String, Object>>() {});
                        return Mono.just(map);
                    } catch (Exception e) {
                        log.error("❌ JSON mapping failed for getWebhookInfo response", e);
                        return Mono.error(new RuntimeException("JSON 解析失败", e));
                    }
                })
                .onErrorResume(e -> {
                    // ... 错误处理逻辑保持不变
                    log.error("❌ Telegram getWebhookInfo API call failed for token: {}", token, e);
                    return Mono.just(Map.of("ok", false, "description", "Telegram API call failed: " + e.getMessage()));
                });
    }

    /**
     * 响应用户的 Callback Query (按钮点击)，通常用于消除按钮上的加载动画。
     * 这解决了 BotController.java 中的 "找不到符号" answerCallbackQuery 错误。
     */
    public Mono<Void> answerCallbackQuery(String token, String callbackQueryId, String text) {
        String path = "/bot" + token + "/answerCallbackQuery";

        // 构建 URI，包括 callback_query_id 和 text
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromPath(path)
                .queryParam("callback_query_id", callbackQueryId)
                .queryParam("text", text)
                .queryParam("show_alert", false); // 默认不显示警报

        return telegramWebClient.get()
                .uri(uriBuilder.build().encode().toUriString())
                .retrieve()
                .toBodilessEntity() // 不需要响应体，只关心状态
                .doOnSuccess(response -> log.debug("Callback query answered successfully: {}", callbackQueryId))
                .onErrorResume(e -> {
                    log.error("❌ Failed to answer callback query: {}", callbackQueryId, e);
                    return Mono.empty();
                })
                .then(); // 转换为 Mono<Void>
    }

    /**
     * 发送消息给 Telegram 用户/群组。
     * 【重要修复】：切换到 POST 请求，并将所有参数（包括 reply_markup 的 JSON）放入请求体中，
     * 以避免 Telegram 在 GET 请求中对复杂 JSON 负载的 400 错误。
     */
    public Mono<Void> sendMessage(String token, Long chatId, String text, Object replyMarkup) {

        String path = "/bot" + token + "/sendMessage";

        // 1. 构建请求体 Map
        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("chat_id", chatId);
        bodyMap.put("text", text);

        if (replyMarkup != null) {
            // Telegram 要求 reply_markup 是一个 JSON 对象
            bodyMap.put("reply_markup", replyMarkup);
        }

        // 2. 执行 POST 请求
        return telegramWebClient.post()
                .uri(path)
                // 使用 BodyInserters.fromValue(bodyMap) 将 Map 自动序列化为 JSON 请求体
                .body(BodyInserters.fromValue(bodyMap))
                .retrieve()
                .toBodilessEntity() // 不需要响应体，只关心状态
                .doOnSuccess(response -> log.info("Message sent successfully to chatId: {}", chatId))
                // ❗ 最终修复: 处理连接在应用关闭时的 PrematureCloseException
                .onErrorResume(PrematureCloseException.class, e -> {
                    // 在优雅关闭成功后，这属于预期情况，只记录 WARN
                    log.warn("Asynchronous message send failed due to connection premature closure during shutdown for chatId: {}", chatId);
                    return Mono.empty(); // 失败时吞掉异常，返回完成信号
                })
                .onErrorResume(e -> {
                    log.error("❌ Failed to send message to chatId: {}, Error: {}", chatId, e.getMessage());
                    return Mono.empty(); // 失败时吞掉异常，返回完成信号
                })
                .then(); // 转换为 Mono<Void>
    }

    // 重载方法：兼容不带键盘的调用
    public Mono<Void> sendMessage(String token, Long chatId, String text) {
        return sendMessage(token, chatId, text, null);
    }
}