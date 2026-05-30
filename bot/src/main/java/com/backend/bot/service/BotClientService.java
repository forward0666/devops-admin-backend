package com.backend.bot.service;

import java.util.concurrent.ConcurrentHashMap;

import com.backend.bot.config.TelegramProperties;
import com.backend.bot.constants.TelegramConstants;
import com.backend.bot.dto.InlineKeyboardMarkupDto;
import com.backend.bot.util.ErrorHandlerUtils;
import com.backend.bot.util.LogUtils;
import com.backend.bot.util.RetryUtil;
import com.backend.bot.util.TelegramApiRequestBuilder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.PrematureCloseException;
import reactor.util.context.ContextView;
import reactor.util.retry.Retry;
import reactor.core.scheduler.Scheduler;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class BotClientService {

    // 静态常量，用于在 Reactor Context 中存储 Trace ID 的 Key
    public static final String TRACE_ID_CONTEXT_KEY = "traceId";

    // 编辑消息文本缓存，避免重复编辑相同内容
    private final ConcurrentHashMap<String, String> editTextCache = new ConcurrentHashMap<>();

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
     * Helper to safely retrieve the trace ID prefix from Reactor Context (preferred) or MDC (fallback).
     *
     * @param contextView Reactor ContextView，用于跨线程传播 Trace ID。
     * @return 格式化后的日志前缀，例如 "[traceId=xyz]" 或 "[traceId=N/A]"。
     */
    private String getTraceIdPrefix(ContextView contextView) {
        String traceId = contextView.getOrDefault(TRACE_ID_CONTEXT_KEY, null);

        // Fallback: 如果 Context 中没有，尝试从 MDC 获取 (用于处理非 WebFlux 启动的流或异常线程)
        if (traceId == null) {
            traceId = MDC.get(LogUtils.TRACE_ID_KEY);
        }

        return LogUtils.buildTraceIdLogPrefix(traceId);
    }

    /**
     * 注册/更新 Webhook URL。
     */
    public Mono<String> setWebhook(String token, String fullWebhookUrl, String secretToken) {
        return TelegramApiRequestBuilder.setWebhook(token, fullWebhookUrl, secretToken)
                .retryPolicy(telegramRetryPolicy)
                .executeWithResponse(telegramWebClient, telegramRetryPolicy)
                .onErrorResume(e -> Mono.just("{\"ok\":false, \"description\":\"Telegram API error: " + e.getMessage() + "\"}"));
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
                .retryWhen(telegramRetryPolicy)
                .publishOn(blockingTaskScheduler)
                .flatMap(jsonResponse -> Mono.deferContextual(contextView -> {
                    String prefix = getTraceIdPrefix(contextView);
                    try {
                        Map<String, Object> map = objectMapper.readValue(jsonResponse, new TypeReference<Map<String, Object>>() {});
                        return Mono.just(map);
                    } catch (Exception e) {
                        log.error("{}❌ JSON mapping failed for getWebhookInfo response", prefix, e);
                        return Mono.error(new RuntimeException("JSON 解析失败", e));
                    }
                }))
                .onErrorResume(e -> Mono.deferContextual(contextView -> {
                    String prefix = getTraceIdPrefix(contextView);
                    log.error("{}❌ Telegram getWebhookInfo API call failed for token: {}", prefix, token, e);
                    return Mono.just(Map.of("ok", false, "description", "Telegram API call failed: " + e.getMessage()));
                }));
    }

    /**
     * 响应用户的 Callback Query (按钮点击)，通常用于消除按钮上的加载动画。
     */
    public Mono<Void> answerCallbackQuery(String token, String callbackQueryId, String text) {
        String path = String.format(TelegramConstants.API_PATH_TEMPLATE, token, "answerCallbackQuery");

        return TelegramApiRequestBuilder.create()
                .path(path)
                .method(TelegramApiRequestBuilder.RequestMethod.GET)
                .customParam("callback_query_id", callbackQueryId)
                .customParam("text", text)
                .customParam("show_alert", false)
                .retryPolicy(telegramRetryPolicy)
                .logTemplates("✅ Callback query answered successfully: " + callbackQueryId,
                        "Failed to answer callback query: " + callbackQueryId)
                .executeWithoutResponse(telegramWebClient, telegramRetryPolicy);
    }

    /**
     * 重载方法：只接受 token 和 callbackQueryId，不显示任何文本
     */
    public Mono<Void> answerCallbackQuery(String token, String callbackQueryId) {
        return answerCallbackQuery(token, callbackQueryId, "");
    }

    /**
     * 发送消息给 Telegram 用户/群组。
     */
    public Mono<Void> sendMessage(String token, Long chatId, String text, Object replyMarkup, String chatName) {
        String logIdentifier = chatName != null && !chatName.isBlank() ? chatName : String.valueOf(chatId);

        return TelegramApiRequestBuilder.sendMessage(token)
                .chatId(chatId)
                .text(text)
                .replyMarkup(replyMarkup)
                .retryPolicy(telegramRetryPolicy)
                .logTemplates("✅ Message sent successfully to Chat: " + logIdentifier,
                        "Failed to send message to Chat: " + logIdentifier)
                .executeWithoutResponse(telegramWebClient, telegramRetryPolicy)
                .onErrorResume(PrematureCloseException.class,
                        ErrorHandlerUtils.logAndWarn("asynchronous message send due to connection premature closure"));
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
     */
    public Mono<String> sendMenuMessageWithResponse(String token, Long chatId, String text, InlineKeyboardMarkupDto replyMarkup, String chatName) {
        String path = "/bot" + token + "/sendMessage";

        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("chat_id", chatId);
        bodyMap.put("text", text);

        if (replyMarkup != null) {
            bodyMap.put("reply_markup", replyMarkup);
        }

        return telegramWebClient.post()
                .uri(path)
                .body(BodyInserters.fromValue(bodyMap))
                .exchangeToMono(response -> {
                    if (response.statusCode().isError()) {
                        return response.bodyToMono(String.class)
                                .defaultIfEmpty("no body")
                                .flatMap(body -> {
                                    log.error("❌ TG API error {} for chatId={}: {}", response.statusCode(), chatId, body);
                                    return Mono.error(new RuntimeException("TG API " + response.statusCode() + ": " + body));
                                });
                    }
                    return response.bodyToMono(String.class)
                            .defaultIfEmpty("{}");
                })
                .retryWhen(telegramRetryPolicy)
                .flatMap(response -> Mono.deferContextual(contextView -> {
                    String prefix = getTraceIdPrefix(contextView);
                    String logIdentifier = chatName != null && !chatName.isBlank() ? chatName : String.valueOf(chatId);
                    return Mono.just(response);
                }))
                .onErrorResume(e -> Mono.deferContextual(contextView -> {
                    String prefix = getTraceIdPrefix(contextView);
                    log.error("{}❌ Failed to send message with response to chatId: {}. Error: {}", prefix, chatId, e.getMessage());
                    return Mono.error(e);
                }));
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
        bodyMap.put("reply_markup", replyMarkup);

        return telegramWebClient.post()
                .uri(path)
                .body(BodyInserters.fromValue(bodyMap))
                .retrieve()
                .toBodilessEntity()
                .retryWhen(telegramRetryPolicy)
                .then(
                        Mono.<Void>deferContextual(contextView -> {
                            String prefix = getTraceIdPrefix(contextView);
                            log.debug("{}✅ Message markup edited successfully for chatId: {}", prefix, chatId);
                            return Mono.empty();
                        })
                )
                .onErrorResume(e ->
                        Mono.<Void>deferContextual(contextView -> {
                            String prefix = getTraceIdPrefix(contextView);
                            log.error("{}❌ Failed to edit message markup to chatId: {}, messageId: {}. Error: {}", prefix, chatId, messageId, e.getMessage());
                            // 必须返回 Mono<Void> 类型的错误
                            return Mono.<Void>error(e);
                        })
                );
    }

    /**
     * 编辑已发送消息的文本内容和/或键盘。
     * 如果 replyMarkup 为 null，则移除键盘。
     * * 🌟 关键修改：在 API 层处理 400 Bad Request 错误，防止日志污染。
     */
    public Mono<Void> editMessageText(String token, Long chatId, Long messageId, String text, InlineKeyboardMarkupDto replyMarkup) {
        // 缓存key
        String cacheKey = chatId + ":" + messageId;
        String lastText = editTextCache.get(cacheKey);
        if (text != null && text.equals(lastText)) {
            return Mono.<Void>deferContextual(ctx -> {
                log.debug("{}💬 Skipping editMessageText - text unchanged for messageId: {}", getTraceIdPrefix(ctx), messageId);
                return Mono.empty();
            });
        }
        editTextCache.put(cacheKey, text);
        String path = "/bot" + token + "/editMessageText";

        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("chat_id", chatId);
        bodyMap.put("message_id", messageId);
        bodyMap.put("text", text);

        if (replyMarkup != null) {
            bodyMap.put("reply_markup", replyMarkup);
        }

        return telegramWebClient.post()
                .uri(path)
                .body(BodyInserters.fromValue(bodyMap))
                .retrieve()
                .toBodilessEntity()
                .retryWhen(telegramRetryPolicy)
                .then(
                        Mono.<Void>deferContextual(contextView -> {
                            String prefix = getTraceIdPrefix(contextView);
                            log.debug("{}✅ Message text edited successfully for chatId: {}", prefix, chatId);
                            return Mono.empty();
                        })
                )
                // 🌟 关键修改：在 BotClientService 中捕获 WebClientResponseException
                .onErrorResume(WebClientResponseException.class, e -> {
                    // 如果是 400 Bad Request，通常是因为消息内容未修改，或者消息不存在
                    if (e.getStatusCode().value() == 400) {
                        return Mono.<Void>deferContextual(contextView -> {
                            String prefix = getTraceIdPrefix(contextView);
                            // 降级为 INFO 日志，并说明原因
                            log.info("{}💬 Could not edit message text for chatId: {}, messageId: {}. Reason: 400 Bad Request (Likely no modification occurred).",
                                    prefix, chatId, messageId);
                            // 返回 Mono.empty() 阻止错误向上游传播
                            return Mono.empty();
                        });
                    }

                    // 对于其他 WebClient 错误，继续打印 WARN/ERROR 并抛出
                    return Mono.<Void>deferContextual(contextView -> {
                        String prefix = getTraceIdPrefix(contextView);
                        log.error("{}❌ Failed to edit message text for chatId: {}, messageId: {}. Error: {}", prefix, chatId, messageId, e.getMessage());
                        return Mono.<Void>error(e);
                    });
                })
                .onErrorResume(e ->
                        Mono.<Void>deferContextual(contextView -> {
                            String prefix = getTraceIdPrefix(contextView);
                            log.error("{}❌ Failed to edit message text for chatId: {}, messageId: {}. Error: {}", prefix, chatId, messageId, e.getMessage());
                            return Mono.<Void>error(e);
                        })
                );
    }

    /**
     * 【新增】删除指定消息。用于菜单超时自动销毁。
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
                .then(
                        Mono.<Void>deferContextual(contextView -> {
                            String prefix = getTraceIdPrefix(contextView);
                            log.warn("{}✅ Message {} deleted successfully in chatId: {}", prefix, messageId, chatId);
                            return Mono.empty();
                        })
                )
                .onErrorResume(e ->
                        Mono.<Void>deferContextual(contextView -> {
                            String prefix = getTraceIdPrefix(contextView);
                            // Telegram API 如果消息已不存在会返回 400 错误，这里忽略它
                            log.warn("{}⚠️ Failed to delete message {} in chatId: {}. May already be deleted. Error: {}", prefix, messageId, chatId, e.getMessage());
                            return Mono.empty();
                        })
                );
    }

    public Mono<String> deleteWebhook(String token) {
        String url = telegramProperties.getApiBaseUrl() + "/bot" + token + "/deleteWebhook?drop_pending_updates=true";
        return telegramWebClient.post()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class);
    }

    public Mono<Long> createForumTopic(String token, Long chatId, String topicName) {
        String url = telegramProperties.getApiBaseUrl() + "/bot" + token + "/createForumTopic";
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("chat_id", chatId);
        body.put("name", topicName);
        return telegramWebClient.post()
                .uri(url)
                .bodyValue(body)
                .exchangeToMono(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        return response.bodyToMono(String.class)
                                .map(resp -> {
                                    try {
                                        ObjectMapper om = new ObjectMapper();
                                        com.fasterxml.jackson.databind.JsonNode root = om.readTree(resp);
                                        return root.path("result").path("message_thread_id").asLong(0);
                                    } catch (Exception e) {
                                        log.error("Failed to parse createForumTopic response", e);
                                        return 0L;
                                    }
                                });
                    }
                    return response.bodyToMono(String.class)
                            .flatMap(errorBody -> {
                                log.error("createForumTopic failed: status={}, body={}", response.statusCode(), errorBody);
                                return Mono.error(new RuntimeException("createForumTopic failed: " + errorBody));
                            });
                });
    }

    public Mono<Void> sendMessageToThread(String token, Long chatId, Long threadId, String text) {
        String url = telegramProperties.getApiBaseUrl() + "/bot" + token + "/sendMessage";
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("chat_id", chatId);
        body.put("message_thread_id", threadId);
        body.put("text", text);
        return telegramWebClient.post()
                .uri(url)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(res -> log.debug("sendMessageToThread response: {}", res))
                .then();
    }

    public Mono<Void> editForumTopic(String token, Long chatId, Long threadId, String topicName) {
        String url = telegramProperties.getApiBaseUrl() + "/bot" + token + "/editForumTopic";
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("chat_id", chatId);
        body.put("message_thread_id", threadId);
        body.put("name", topicName);
        return telegramWebClient.post()
                .uri(url)
                .bodyValue(body)
                .exchangeToMono(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        return response.bodyToMono(String.class).then();
                    }
                    return response.bodyToMono(String.class)
                            .doOnNext(errorBody -> log.warn("editForumTopic failed: {}", errorBody))
                            .then();
                });
    }
}
