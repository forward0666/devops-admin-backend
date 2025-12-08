package com.backend.bot.controller;

import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.event.BotUpdateEvent;
import com.backend.bot.util.LogUtils;
import filter.TraceIdFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Optional;

import static com.backend.bot.util.BotUpdateUtils.extractChatId;

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
//                     1. 从 Context 中获取 Trace ID (此步骤无法抽象)
                    String traceId = contextView.hasKey(TraceIdFilter.CONTEXT_KEY_TRACE_ID)
                            ? contextView.get(TraceIdFilter.CONTEXT_KEY_TRACE_ID).toString()
                            : null;

                    // 2. [可选但强烈推荐] 手动同步 MDC (使用工具类)
                    LogUtils.syncTraceIdToMDC(traceId); // 🌟 抽象

//                     3. 构建 Trace ID 日志标识 (使用工具类)
                    String traceIdLog = LogUtils.buildTraceIdLogPrefix(traceId); // 🌟 抽象

//                     4. 构建 Chat ID 日志 (使用工具类)
                    Optional<Long> chatIdOpt = extractChatId(botUpdate);
                    String chatIdLog = LogUtils.buildChatIdLogSuffix(chatIdOpt);

                    // 简单的日志记录，证明请求已到达
                    log.info("{} ✅ [Webhook] Received update for bot: {}{}. Publishing event...", traceIdLog, botName, chatIdLog);
//                    LogUtils.processWebhookUpdateAndPublishEvent(contextView, eventPublisher, botName, botUpdate);
                    // 1. 发布事件
                    eventPublisher.publishEvent(new BotUpdateEvent(botName, botUpdate, traceId));

                    // 2. 立即返回 Mono.empty() (HTTP 200 OK)
                    return Mono.empty();
                }
        );
    }
}