package com.backend.bot.controller;

import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.event.BotUpdateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

        // 简单的日志记录，证明请求已到达
        Optional<Long> chatIdOpt = extractChatId(botUpdate);
        String chatIdLog = chatIdOpt.map(id -> " (Chat ID: " + id + ")").orElse("");
        log.info("✅ [Webhook] Received update for bot: {}{}. Publishing event...", botName, chatIdLog);

        // 1. 发布事件 (这是同步非阻塞的，仅仅是将对象放入 Spring 事件系统)
        // 实际处理逻辑将在 BotUpdateListener 中异步执行
        eventPublisher.publishEvent(new BotUpdateEvent(botName, botUpdate));

        // 2. 立即返回 Mono.empty() (HTTP 200 OK)
        // Telegram 服务器会立即收到确认，不会因为业务逻辑处理慢而重试
        return Mono.empty();
    }
}