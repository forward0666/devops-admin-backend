package com.backend.bot.controller;

import com.backend.bot.dto.BotUpdateDto;
// import com.backend.bot.event.BotUpdateEvent; // 🌟 移除：不再直接使用
import com.backend.bot.util.LogUtils;
// import filter.TraceIdFilter; // 🌟 移除：不再直接使用
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

// import java.util.Optional; // 🌟 移除：不再直接使用

// import static com.backend.bot.util.BotUpdateUtils.extractChatId; // 🌟 移除：不再直接使用

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

        // 🌟 关键修改：将日志记录、MDC同步和事件发布封装到 LogUtils 中执行
        return Mono.deferContextual(contextView ->
                        LogUtils.processWebhookUpdateAndPublishEvent(
                                contextView,
                                eventPublisher,
                                botName,
                                botUpdate
                        )
                )
                // 🌟 确保在响应完成后，清除 MDC
                .doFinally(LogUtils::clearMDC);
    }
}