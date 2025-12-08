package com.backend.bot.util;

import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.event.BotUpdateEvent;
import filter.TraceIdFilter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import java.util.Optional;

import static com.backend.bot.util.BotUpdateUtils.extractChatId;

@Slf4j
public class LogUtils {

    private static final String TRACE_ID_KEY = "traceId";

    // --- 核心方法：封装整个 Webhook 日志和事件发布流程 ---

    /**
     * 封装 Webhook 端点接收更新后的日志记录、MDC同步和事件发布逻辑。
     *
     * @param contextView Reactor ContextView，用于获取 Trace ID。
     * @param eventPublisher Spring 事件发布器。
     * @param botName Bot 名称。
     * @param botUpdate Webhook 接收到的 BotUpdate DTO。
     * @return 总是返回 Mono.empty()，用于 Webhook 立即响应 200 OK。
     */
    public static Mono<Void> processWebhookUpdateAndPublishEvent(
            ContextView contextView,
            ApplicationEventPublisher eventPublisher,
            String botName,
            BotUpdateDto botUpdate) {

        // 1. 从 Context 中获取 Trace ID (此步骤无法抽象到 LogUtils 外部)
        String traceId = contextView.hasKey(TraceIdFilter.CONTEXT_KEY_TRACE_ID)
                ? contextView.get(TraceIdFilter.CONTEXT_KEY_TRACE_ID).toString()
                : null;

        // 2. [可选但强烈推荐] 手动同步 MDC，确保后续异步链中的日志都能打印 Trace ID
        LogUtils.syncTraceIdToMDC(traceId);

        // 3. 构建 Trace ID 日志标识
        String traceIdLog = LogUtils.buildTraceIdLogPrefix(traceId);

        // 4. 构建 Chat ID 日志
        Optional<Long> chatIdOpt = extractChatId(botUpdate);
        String chatIdLog = LogUtils.buildChatIdLogSuffix(chatIdOpt);

        // 5. 日志记录
        log.info("{} ✅ [Webhook] Received update for bot: {}{}. Publishing event...",
                traceIdLog, botName, chatIdLog);

        // 6. 发布事件 (携带 Trace ID)
        eventPublisher.publishEvent(new BotUpdateEvent(botName, botUpdate, traceId));

        // 7. 立即返回
        return Mono.empty();
    }

    // --- 辅助方法 (供其他 Controller 使用) ---

    /**
     * 辅助方法：将 Trace ID 写入 SLF4J 的 MDC，以便 Logback/Log4j2 能够捕获它。
     * @param traceId 从 Reactor Context 中提取的 Trace ID 字符串
     */
    public static void syncTraceIdToMDC(String traceId) {
        if (traceId != null) {
            MDC.put(TRACE_ID_KEY, traceId);
        }
    }

    /**
     * 根据传入的 traceId 字符串构建日志前缀。
     * @param traceId 从 Reactor Context 或 MDC 中提取的 Trace ID 字符串
     * @return 格式化后的日志前缀，例如 "[traceId=xyz]" 或 "[traceId=null]"
     */
    public static String buildTraceIdLogPrefix(String traceId) {
        return traceId != null
                ? String.format("[%s=%s]", TRACE_ID_KEY, traceId)
                : String.format("[%s=null]", TRACE_ID_KEY);
    }

    /**
     * 辅助方法：格式化 Chat ID 字符串。
     * @param chatIdOpt Optional<Long> 包含 Chat ID
     * @return 格式化后的 Chat ID 字符串，例如 " (Chat ID: 12345)" 或 ""
     */
    public static String buildChatIdLogSuffix(Optional<Long> chatIdOpt) {
        return chatIdOpt.map(id -> String.format(" (Chat ID: %s)", id)).orElse("");
    }
}