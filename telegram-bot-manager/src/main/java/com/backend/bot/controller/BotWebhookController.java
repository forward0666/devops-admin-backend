package com.backend.bot.controller;

import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.event.BotUpdateEvent;
import filter.TraceIdFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Optional;

import static com.backend.bot.utils.BotUpdateUtils.extractChatId;

@RestController
@RequiredArgsConstructor
@Slf4j
public class BotWebhookController {

    private final ApplicationEventPublisher eventPublisher;

    /**
     * 接收 Telegram Webhook 更新的端点。
     * 实现：发布事件 -> 立即返回 200 OK
     */
    @PostMapping("/callback/{botName}")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Void> onUpdateReceived(
            @PathVariable String botName,
            @RequestBody BotUpdateDto botUpdate) {

        // 🌟 关键修改：使用 Mono.deferContextual 获取 Context 中的 Trace ID
        return Mono.deferContextual(contextView -> {
                    // 1. 从 Context 中获取 Trace ID
                    String traceId = contextView.hasKey(TraceIdFilter.CONTEXT_KEY_TRACE_ID)
                            ? contextView.get(TraceIdFilter.CONTEXT_KEY_TRACE_ID).toString()
                            : null; // 如果找不到，则为 null

                    // 2. [可选但强烈推荐] 手动同步 MDC，确保后续异步链中的日志都能打印 Trace ID
                    if (traceId != null) {
                        MDC.put("traceId", traceId);
                    }

                    // 3. 构建日志标识
                    // 注意：如果你使用 Logback/Log4j2 的 MDC 格式化（%X{traceId}），这里的 MDC.get() 将不再必要
                    String traceIdLog = traceId != null ? String.format("[traceId=%s]", traceId) : "[traceId=null]";

                    // 简单的日志记录，证明请求已到达
                    Optional<Long> chatIdOpt = extractChatId(botUpdate);
                    String chatIdLog = chatIdOpt.map(id -> " (Chat ID: " + id + ")").orElse("");
                    log.info("{} ✅ [Webhook] Received update for bot: {}{}. Publishing event...", traceIdLog, botName, chatIdLog);

                    // 1. 发布事件 (这是同步非阻塞的，仅仅是将对象放入 Spring 事件系统)
                    // 实际处理逻辑将在 BotUpdateListener 中异步执行
                    eventPublisher.publishEvent(new BotUpdateEvent(botName, botUpdate));

                    // 2. 立即返回 Mono.empty() (HTTP 200 OK)
                    // Telegram 服务器会立即收到确认，不会因为业务逻辑处理慢而重试
                    return Mono.empty();
                }
        );
    }
}