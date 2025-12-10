package com.backend.bot.util;

import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.event.BotUpdateEvent;
import filter.TraceIdFilter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.util.context.ContextView;

import java.util.Optional;

import static com.backend.bot.util.BotChatUtils.extractChatId;

/**
 * 日志工具类，专注于在 Reactor WebFlux 环境中处理 Trace ID (MDC) 同步和日志格式化。
 */
@Slf4j
public final class LogUtils {

    // Trace ID 的 MDC 键名
    public static final String TRACE_ID_KEY = "traceId";

    // --- 核心方法：封装整个 Webhook 日志和事件发布流程 ---

    /**
     * 封装 Webhook 端点接收更新后的日志记录、MDC同步和事件发布逻辑。
     * * @param contextView Reactor ContextView，用于获取 Trace ID。
     * @param eventPublisher Spring 事件发布器。
     * @param botName Bot 名称。
     * @param botUpdate Webhook 接收到的 BotUpdate DTO。
     * @return 总是返回 Mono.empty()，用于 Webhook 立即响应 200 OK。
     */
    public static Mono<Void> processWebhookUpdateAndPublishEvent(
            ContextView contextView,
            ApplicationEventPublisher eventPublisher,
            String botName,
            BotUpdateDto botUpdate
    ) {
        // 从 Context 中获取 Trace ID，并同步到 MDC
        syncTraceIdToMDC(contextView);
        String traceId = MDC.get(TRACE_ID_KEY);

        String traceIdLog = buildTraceIdLogPrefix(traceId);

        Optional<Long> chatIdOpt = extractChatId(botUpdate);
        String chatIdLog = buildChatIdLogSuffix(chatIdOpt);

        log.info("{}✅ [Webhook] Received update for bot: {}{}. Publishing event...",
                traceIdLog, botName, chatIdLog);

        // 发布事件，将 Trace ID 一起传递给监听器
        eventPublisher.publishEvent(new BotUpdateEvent(botName, botUpdate, traceId));

        return Mono.empty();
    }

    // --- 辅助方法 (供其他 Controller/Service 使用) ---

    /**
     * 辅助方法：在反应式流结束时执行 MDC 清理操作。
     * 应该在 doFinally(LogUtils::clearMDC) 中调用。
     *
     * @param signalType 反应式流的结束信号类型 (ON_COMPLETE, ON_ERROR, CANCEL)。
     */
    public static void clearMDC(SignalType signalType) {
        MDC.clear();
    }

    /**
     * 辅助方法：将 Trace ID 写入 SLF4J 的 MDC，以便 Logback/Log4j2 能够捕获它。
     * * @param traceId 从 Reactor Context 中提取的 Trace ID 字符串
     */
    public static void syncTraceIdToMDC(String traceId) {
        if (traceId != null) {
            MDC.put(TRACE_ID_KEY, traceId);
        } else {
            // 如果 traceId 为空，确保 MDC 中该键不存在或清空，避免携带上一个请求的 traceId
            MDC.remove(TRACE_ID_KEY);
        }
    }

    /**
     * 辅助方法：从 ContextView 中同步 Trace ID 到 MDC。
     * * @param contextView 当前的 ContextView
     */
    public static void syncTraceIdToMDC(ContextView contextView) {
        // 使用 TraceIdFilter 中定义的键名来获取 Trace ID
        contextView.getOrEmpty(TraceIdFilter.CONTEXT_KEY_TRACE_ID)
                .ifPresentOrElse(
                        // 确保使用 TRACE_ID_KEY 放入 MDC
                        traceId -> MDC.put(TRACE_ID_KEY, traceId.toString()),
                        () -> MDC.remove(TRACE_ID_KEY) // 如果 Context 中没有，则清理 MDC
                );
    }

    /**
     * 根据传入的 traceId 字符串构建日志前缀。
     * * @param traceId 从 Reactor Context 或 MDC 中提取的 Trace ID 字符串
     * @return 格式化后的日志前缀，例如 "[traceId=xyz]" 或 "[traceId=N/A]"
     */
    public static String buildTraceIdLogPrefix(String traceId) {
        // 使用 "N/A" 替换 "null"，使其与原始日志中的错误格式一致，方便排查。
        return traceId != null
                ? String.format("[%s=%s]", TRACE_ID_KEY, traceId)
                : String.format("[%s=N/A]", TRACE_ID_KEY);
    }

    /**
     * 辅助方法：格式化 Chat ID 字符串。
     * * @param chatIdOpt Optional<Long> 包含 Chat ID
     * @return 格式化后的 Chat ID 字符串，例如 " (Chat ID: 12345)" 或 ""
     */
    public static String buildChatIdLogSuffix(Optional<Long> chatIdOpt) {
        return chatIdOpt.map(id -> String.format(" (Chat ID: %s)", id)).orElse("");
    }

    /**
     * 将 Trace ID 从 Reactor Context 同步到 MDC，并返回用于强制打印的日志前缀。
     * 这是 Controller/Handler 中设置 MDC 和获取前缀的统一入口。
     * (保留此方法，供 UpdateHandler 内部使用)
     *
     * @param contextView Reactor ContextView
     * @return 格式化后的日志前缀 (e.g., "[traceId=xyz-SIN]")，如果 Trace ID 不存在则返回 "[traceId=N/A]"。
     */
    public static String prepareMdcAndGetPrefix(ContextView contextView) {
        // 1. 将 Trace ID 从 Reactor Context 同步到 MDC
        syncTraceIdToMDC(contextView);

        // 2. 从 MDC 中获取 Trace ID
        String traceId = MDC.get(TRACE_ID_KEY);

        // 3. 构建并返回日志前缀 (使用新的更健壮的格式)
        return buildTraceIdLogPrefix(traceId);
    }
}