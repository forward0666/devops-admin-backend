package com.backend.bot.service;

import com.backend.bot.config.TelegramProperties;
import com.backend.bot.constants.TelegramConstants;
import com.backend.bot.dto.InlineKeyboardMarkupDto;
import com.backend.bot.util.DtoFieldExtractor;
import com.backend.bot.util.ErrorHandlerUtils;
import com.backend.bot.util.LogUtils;
import com.backend.bot.util.ReactiveOperationTemplate;
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
import org.springframework.web.util.UriComponentsBuilder;
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
     * 
     * 使用 TelegramApiRequestBuilder 构建请求，遵循 DRY 原则。
     * 统一处理上下文传播、错误处理和日志记录。
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
                // 🌟 关键修改：使用注入的自定义 Scheduler
                .publishOn(blockingTaskScheduler)
                // 关键修正：使用 flatMap 结合 deferContextual 来处理阻塞操作和日志
                .flatMap(jsonResponse -> Mono.deferContextual(contextView -> {
                    String prefix = getTraceIdPrefix(contextView);
                    try {
                        // 阻塞操作：ObjectMapper.readValue()
                        Map<String, Object> map = objectMapper.readValue(jsonResponse, new TypeReference<Map<String, Object>>() {});
                        return Mono.just(map);
                    } catch (Exception e) {
                        // 🌟 显式打印 traceId
                        log.error("{}❌ JSON mapping failed for getWebhookInfo response", prefix, e);
                        return Mono.error(new RuntimeException("JSON 解析失败", e));
                    }
                }))
                .onErrorResume(e -> Mono.deferContextual(contextView -> {
                    String prefix = getTraceIdPrefix(contextView);
                    // 🌟 显式打印 traceId
                    log.error("{}❌ Telegram getWebhookInfo API call failed for token: {}", prefix, token, e);
                    return Mono.just(Map.of("ok", false, "description", "Telegram API call failed: " + e.getMessage()));
                }));
    }

    /**
     * 响应用户的 Callback Query (按钮点击)，通常用于消除按钮上的加载动画。
     * 
     * 使用 TelegramApiRequestBuilder 构建请求，遵循 DRY 原则。
     * 统一处理上下文传播、错误处理和日志记录。
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
     * 发送消息给 Telegram 用户/群组。
     * 
     * 使用 TelegramApiRequestBuilder 构建请求，遵循 DRY 原则。
     * 统一处理上下文传播、错误处理和日志记录。
     * 
     * @param chatName 聊天的名称（可选，用于日志记录）
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
                // 关键修正：使用 flatMap 结合 deferContextual 替代 doOnSuccess，确保上下文在记录日志时不会丢失
                .flatMap(response -> Mono.deferContextual(contextView -> {
                    String prefix = getTraceIdPrefix(contextView);
                    String logIdentifier = chatName != null && !chatName.isBlank() ? chatName : String.valueOf(chatId);
                    log.info("{}✅ Message sent successfully and full JSON response received for Chat: {}", prefix, logIdentifier);
                    return Mono.just(response); // 必须返回原始响应体
                }))
                .onErrorResume(e -> Mono.deferContextual(contextView -> {
                    String prefix = getTraceIdPrefix(contextView);
                    log.error("{}❌ Failed to send message with response to chatId: {}. Error: {}", prefix, chatId, e.getMessage());
                    return Mono.error(e); // 向上抛出错误
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

        // Telegram 要求 reply_markup 是一个 JSON 对象
        bodyMap.put("reply_markup", replyMarkup);

        return telegramWebClient.post()
                .uri(path)
                .body(BodyInserters.fromValue(bodyMap))
                .retrieve()
                .toBodilessEntity()
                .retryWhen(telegramRetryPolicy)
                // Fix 1: 使用 .then() 转换类型，并确保内部 deferContextual 显式类型化
                .then(
                        Mono.<Void>deferContextual(contextView -> {
                            String prefix = getTraceIdPrefix(contextView);
                            log.debug("{}✅ Message markup edited successfully for chatId: {}", prefix, chatId);
                            return Mono.empty();
                        })
                )
                // Fix 2: 显式类型化 onErrorResume 的 fallback 函数
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
                // Fix 1: 使用 .then() 转换类型，并确保内部 deferContextual 显式类型化
                .then(
                        Mono.<Void>deferContextual(contextView -> {
                            String prefix = getTraceIdPrefix(contextView);
                            log.debug("{}✅ Message text edited successfully for chatId: {}", prefix, chatId);
                            return Mono.empty();
                        })
                )
                // Fix 2: 显式类型化 onErrorResume 的 fallback 函数
                .onErrorResume(e ->
                        Mono.<Void>deferContextual(contextView -> {
                            String prefix = getTraceIdPrefix(contextView);
                            log.error("{}❌ Failed to edit message text for chatId: {}, messageId: {}. Error: {}", prefix, chatId, messageId, e.getMessage());
                            // 必须返回 Mono<Void> 类型的错误
                            return Mono.<Void>error(e);
                        })
                );
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
                // Fix 1: 使用 .then() 转换类型，并确保内部 deferContextual 显式类型化
                .then(
                        Mono.<Void>deferContextual(contextView -> {
                            String prefix = getTraceIdPrefix(contextView);
                            log.warn("{}✅ Message {} deleted successfully in chatId: {}", prefix, messageId, chatId);
                            return Mono.empty();
                        })
                )
                // Fix 2: 显式类型化 onErrorResume 的 fallback 函数
                .onErrorResume(e ->
                        Mono.<Void>deferContextual(contextView -> {
                            String prefix = getTraceIdPrefix(contextView);
                            // Telegram API 如果消息已不存在会返回 400 错误，这里忽略它
                            log.warn("{}⚠️ Failed to delete message {} in chatId: {}. May already be deleted. Error: {}", prefix, messageId, chatId, e.getMessage());
                            return Mono.empty();
                        })
                );
    }
}