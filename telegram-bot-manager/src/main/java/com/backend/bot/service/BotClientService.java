package com.backend.bot.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient; // 替换 RestTemplate
import reactor.core.publisher.Mono;

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
     * 【转换点 1】：返回 Mono<String>
     * 【转换点 2】：使用 WebClient 替代 RestTemplate
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
                    log.error("Telegram setWebhook API call failed for token: {}", token, e);
                    // 返回一个包含错误信息的 Mono
                    return Mono.just("{\"ok\":false, \"description\":\"Telegram API error: " + e.getMessage() + "\"}");
                })
                .doOnSuccess(response -> log.info("setWebhook response received successfully."));
    }

    /**
     * 获取 Bot 的 Webhook 状态及 pending 消息数。
     * 【转换点 1】：返回 Mono<Map<String, Object>>
     */
//    public Mono<Map<String, Object>> getWebhookInfo(String token) {
//
//        String path = "/bot" + token + "/getWebhookInfo";
//
//        return telegramWebClient.get()
//                .uri(path)
//                .retrieve()
//                .bodyToMono(String.class)
//                .flatMap(jsonResponse -> {
//                    // ⚠️ JSON 序列化仍然是阻塞操作，理想情况下应在单独的线程上执行 (如 Schedulers.boundedElastic)
//                    try {
//                        Map<String, Object> map = objectMapper.readValue(jsonResponse, new TypeReference<Map<String, Object>>() {});
//                        return Mono.just(map);
//                    } catch (Exception e) {
//                        log.error("JSON mapping failed for getWebhookInfo response", e);
//                        return Mono.error(new RuntimeException("JSON 解析失败", e));
//                    }
//                })
//                .onErrorResume(e -> {
//                    log.error("Telegram getWebhookInfo API call failed for token: {}", token, e);
//                    return Mono.just(Map.of("ok", false, "description", "Telegram API call failed: " + e.getMessage()));
//                });
//    }
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
                        log.error("JSON mapping failed for getWebhookInfo response", e);
                        return Mono.error(new RuntimeException("JSON 解析失败", e));
                    }
                })
                .onErrorResume(e -> {
                    // ... 错误处理逻辑保持不变
                    log.error("Telegram getWebhookInfo API call failed for token: {}", token, e);
                    return Mono.just(Map.of("ok", false, "description", "Telegram API call failed: " + e.getMessage()));
                });
    }
    /**
     * 发送消息给 Telegram 用户/群组。
     * 【转换点 1】：返回 Mono<Void>
     */
    public Mono<Void> sendMessage(String token, Long chatId, String text) {

        String path = "/bot" + token + "/sendMessage";

        return telegramWebClient.get()
                .uri(path, uriBuilder -> uriBuilder
                        .queryParam("chat_id", chatId)
                        .queryParam("text", text)
                        .build())
                .retrieve()
                .toBodilessEntity() // 不需要响应体，只关心状态
                .doOnSuccess(response -> log.info("Message sent successfully to chatId: {}", chatId))
                .onErrorResume(e -> {
                    log.error("Failed to send message to chatId: {}, Error: {}", chatId, e.getMessage());
                    return Mono.empty(); // 失败时吞掉异常，返回完成信号
                })
                .then(); // 转换为 Mono<Void>
    }
}