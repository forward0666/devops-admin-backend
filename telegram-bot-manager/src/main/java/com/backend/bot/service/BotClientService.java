package com.backend.bot.service;

import com.backend.bot.config.TelegramProperties;
import com.backend.bot.dto.InlineKeyboardMarkupDto;
import com.backend.bot.util.RetryUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.PrematureCloseException;
import reactor.util.retry.Retry;
import reactor.core.scheduler.Scheduler;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class BotClientService {

    private final WebClient telegramWebClient;
    private final ObjectMapper objectMapper;
    private final TelegramProperties telegramProperties;
    private final Retry telegramRetryPolicy;
    private final Scheduler blockingTaskScheduler;

    public BotClientService(WebClient telegramWebClient,
                            ObjectMapper objectMapper,
                            TelegramProperties telegramProperties,
                            // 注入自定义 Scheduler
                            @Qualifier("blockingTaskScheduler") Scheduler blockingTaskScheduler) {

        this.telegramWebClient = telegramWebClient;
        this.objectMapper = objectMapper;
        this.telegramProperties = telegramProperties;
        this.blockingTaskScheduler = blockingTaskScheduler;

        // 初始化指数退避重试策略：最大重试 3 次，初始退避 100ms
        this.telegramRetryPolicy = RetryUtil.exponentialBackoff(3, Duration.ofMillis(100));
    }

    /**
     * 注册/更新 Webhook URL。
     * 【优化】：应用指数退避重试策略。
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
                // 🚀 应用重试策略
                .retryWhen(telegramRetryPolicy)
                .onErrorResume(e -> {
                    // 🌟 日志依赖 MDC 自动打印 traceId
                    log.error("❌Telegram setWebhook API call failed for token: {}", token, e);
                    return Mono.just("{\"ok\":false, \"description\":\"Telegram API error: " + e.getMessage() + "\"}");
                })
                // 🌟 日志依赖 MDC 自动打印 traceId
                .doOnSuccess(response -> log.info("setWebhook response received successfully."));
    }

    /**
     * 获取 Bot 的 Webhook 状态及 pending 消息数。
     * 【优化】：应用指数退避重试策略。
     */
    public Mono<Map<String, Object>> getWebhookInfo(String token) {
        String path = "/bot" + token + "/getWebhookInfo";

        return telegramWebClient.get()
                .uri(path)
                .retrieve()
                .bodyToMono(String.class)
                .retryWhen(telegramRetryPolicy)

                // 🌟 关键修改：使用注入的自定义 Scheduler
                .publishOn(blockingTaskScheduler)

                .flatMap(jsonResponse -> {
                    try {
                        // 阻塞操作：ObjectMapper.readValue()
                        Map<String, Object> map = objectMapper.readValue(jsonResponse, new TypeReference<Map<String, Object>>() {});
                        return Mono.just(map);
                    } catch (Exception e) {
                        // 🌟 日志依赖 MDC 自动打印 traceId
                        log.error("❌ JSON mapping failed for getWebhookInfo response", e);
                        return Mono.error(new RuntimeException("JSON 解析失败", e));
                    }
                })
                .onErrorResume(e -> {
                    // 🌟 日志依赖 MDC 自动打印 traceId
                    log.error("❌ Telegram getWebhookInfo API call failed for token: {}", token, e);
                    return Mono.just(Map.of("ok", false, "description", "Telegram API call failed: " + e.getMessage()));
                });
    }

    /**
     * 响应用户的 Callback Query (按钮点击)，通常用于消除按钮上的加载动画。
     * 【优化】：应用指数退避重试策略。
     */
    public Mono<Void> answerCallbackQuery(String token, String callbackQueryId, String text) {
        String path = "/bot" + token + "/answerCallbackQuery";

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromPath(path)
                .queryParam("callback_query_id", callbackQueryId)
                .queryParam("text", text)
                .queryParam("show_alert", false);

        return telegramWebClient.get()
                .uri(uriBuilder.build().encode().toUriString())
                .retrieve()
                .toBodilessEntity()
                // 🚀 应用重试策略
                .retryWhen(telegramRetryPolicy)
                // 🌟 日志依赖 MDC 自动打印 traceId
                .doOnSuccess(response -> log.debug("Callback query answered successfully: {}", callbackQueryId))
                .onErrorResume(e -> {
                    // 🌟 日志依赖 MDC 自动打印 traceId
                    log.error("❌ Failed to answer callback query: {}", callbackQueryId, e);
                    return Mono.empty();
                })
                .then();
    }

    /**
     * 发送消息给 Telegram 用户/群组。
     * 【优化】：应用指数退避重试策略。
     * @param chatName 聊天的名称（可选，用于日志记录）
     */
    public Mono<Void> sendMessage(String token, Long chatId, String text, Object replyMarkup, String chatName) {

        String path = "/bot" + token + "/sendMessage";

        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("chat_id", chatId);
        bodyMap.put("text", text);

        if (replyMarkup != null) {
            bodyMap.put("reply_markup", replyMarkup);
        }

        // 2. 执行 POST 请求
        return telegramWebClient.post()
                .uri(path)
                .body(BodyInserters.fromValue(bodyMap))
                .retrieve()
                .toBodilessEntity()
                // 🚀 应用重试策略
                .retryWhen(telegramRetryPolicy)
                // 🌟 日志增强：打印群名称
                .doOnSuccess(response -> {
                    String logIdentifier = chatName != null && !chatName.isBlank() ? chatName : String.valueOf(chatId);
                    log.info("✅ Message sent successfully to Chat: {}", logIdentifier);
                })
                .onErrorResume(PrematureCloseException.class, e -> {
                    // 🌟 日志依赖 MDC 自动打印 traceId
                    log.warn("⚠️ Asynchronous message send failed due to connection premature closure during shutdown for chatId: {}", chatId);
                    return Mono.empty();
                })
                .onErrorResume(e -> {
                    // 🌟 日志依赖 MDC 自动打印 traceId
                    log.error("❌ Failed to send message to chatId: {}, Error: {}", chatId, e.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    // 重载方法：兼容不带键盘和群名称的调用
    public Mono<Void> sendMessage(String token, Long chatId, String text) {
        return sendMessage(token, chatId, text, null, null);
    }

    // 重载方法：兼容不带群名称的调用
    public Mono<Void> sendMessage(String token, Long chatId, String text, Object replyMarkup) {
        return sendMessage(token, chatId, text, replyMarkup, null);
    }


    /**
     * 【新增】发送消息并返回完整的 JSON 响应字符串，用于提取 message_id。
     * @param token 机器人 Token
     * @param chatId 聊天 ID
     * @param text 消息文本
     * @param replyMarkup 内联键盘对象
     * @param chatName 聊天的名称（可选，用于日志记录）
     * @return 包含 Telegram API 响应的 JSON 字符串 Mono
     */
    public Mono<String> sendMenuMessageWithResponse(String token, Long chatId, String text, InlineKeyboardMarkupDto replyMarkup, String chatName) {
        String path = "/bot" + token + "/sendMessage";

        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("chat_id", chatId);
        bodyMap.put("text", text);
        bodyMap.put("parse_mode", "Markdown"); // 确保格式化生效

        if (replyMarkup != null) {
            bodyMap.put("reply_markup", replyMarkup);
        }

        return telegramWebClient.post()
                .uri(path)
                .body(BodyInserters.fromValue(bodyMap))
                .retrieve()
                // 关键点：返回响应体为 String，以便 StartCommandHandler 可以解析
                .bodyToMono(String.class)
                .retryWhen(telegramRetryPolicy)
                // 🌟 日志增强：打印群名称
                .doOnSuccess(response -> {
                    String logIdentifier = chatName != null && !chatName.isBlank() ? chatName : String.valueOf(chatId);
                    log.info("✅ Message sent successfully and full JSON response received for Chat: {}", logIdentifier);
                })
                .onErrorResume(e -> {
                    log.error("❌ Failed to send message with response to chatId: {}. Error: {}", chatId, e.getMessage());
                    return Mono.error(e); // 向上抛出错误
                });
    }

    // 重载方法：兼容不带群名称的调用 (4个参数)
    public Mono<String> sendMenuMessageWithResponse(String token, Long chatId, String text, InlineKeyboardMarkupDto replyMarkup) {
        return sendMenuMessageWithResponse(token, chatId, text, replyMarkup, null);
    }

    /**
     * 替换消息的内联键盘。
     */
    public Mono<Void> editMessageReplyMarkup(String token, Long chatId, Long messageId, InlineKeyboardMarkupDto replyMarkup) {
        String path = "/bot" + token + "/editMessageReplyMarkup";

        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("chat_id", chatId);
        bodyMap.put("message_id", messageId);

        // Telegram 要求 reply_markup 是一个 JSON 对象
        bodyMap.put("reply_markup", replyMarkup);

        return telegramWebClient.post()
                .uri(path)
                .body(BodyInserters.fromValue(bodyMap))
                .retrieve()
                .toBodilessEntity()
                .retryWhen(telegramRetryPolicy)
                .doOnSuccess(response -> log.debug("✅ Message markup edited successfully for chatId: {}", chatId))
                .onErrorResume(e -> {
                    log.error("❌ Failed to edit message markup to chatId: {}, messageId: {}. Error: {}", chatId, messageId, e.getMessage());
                    return Mono.error(e); // 抛出错误，以便上层逻辑（如 Handler）可以处理
                })
                .then();
    }

    /**
     * 编辑已发送消息的文本内容和/或键盘。
     * 如果 replyMarkup 为 null，则移除键盘。
     */
    public Mono<Void> editMessageText(String token, Long chatId, Long messageId, String text, InlineKeyboardMarkupDto replyMarkup) {
        String path = "/bot" + token + "/editMessageText";

        Map<String, Object> bodyMap = new HashMap<>(); // 假设你使用了 HashMap 来构建请求体
        bodyMap.put("chat_id", chatId);
        bodyMap.put("message_id", messageId);
        bodyMap.put("text", text);
        // 启用 Markdown 解析，确保 IP 提示格式正确
        bodyMap.put("parse_mode", "Markdown");

        // 如果提供了键盘，则添加
        if (replyMarkup != null) {
            bodyMap.put("reply_markup", replyMarkup);
        }
        // 如果 replyMarkup 为 null，则不发送该字段，API会移除旧键盘

        return telegramWebClient.post() // 假设你的 WebClient 实例名为 telegramWebClient
                .uri(path)
                .body(BodyInserters.fromValue(bodyMap))
                .retrieve()
                .toBodilessEntity() // 因为这个 API 通常只返回状态
                // 🌟 重要的：添加你的重试策略和错误处理
                .retryWhen(telegramRetryPolicy) // 假设你的重试策略实例名为 telegramRetryPolicy
                .doOnSuccess(response -> log.debug("✅ Message text edited successfully for chatId: {}", chatId))
                .onErrorResume(e -> {
                    log.error("❌ Failed to edit message text for chatId: {}, messageId: {}. Error: {}", chatId, messageId, e.getMessage());
                    return Mono.error(e); // 向上抛出错误，让 CallbackQueryHandler 处理优雅降级
                })
                .then();
    }

    /**
     * 【新增】删除指定消息。用于菜单超时自动销毁。
     * @param token 机器人 Token
     * @param chatId 聊天 ID
     * @param messageId 消息 ID
     * @return Mono<Void>
     */
    public Mono<Void> deleteMessage(String token, Long chatId, Long messageId) {
        String path = "/bot" + token + "/deleteMessage";

        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("chat_id", chatId);
        bodyMap.put("message_id", messageId);

        return telegramWebClient.post()
                .uri(path)
                .body(BodyInserters.fromValue(bodyMap))
                .retrieve()
                .toBodilessEntity()
                .retryWhen(telegramRetryPolicy)
                .doOnSuccess(response -> log.warn("✅ Message {} deleted successfully in chatId: {}", messageId, chatId))
                .onErrorResume(e -> {
                    // Telegram API 如果消息已不存在会返回 400 错误，这里忽略它
                    log.warn("⚠️ Failed to delete message {} in chatId: {}. May already be deleted. Error: {}", messageId, chatId, e.getMessage());
                    return Mono.empty();
                })
                .then();
    }
}