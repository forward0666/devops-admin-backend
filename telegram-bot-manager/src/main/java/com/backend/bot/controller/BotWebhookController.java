package com.backend.bot.controller;

import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.util.LogUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Optional;

import static com.backend.bot.util.BotChatUtils.extractChatId;

@RestController
@RequiredArgsConstructor
@Slf4j
public class BotWebhookController {

    private final ApplicationEventPublisher eventPublisher;
    private final ReactiveStringRedisTemplate redisTemplate;

    @PostMapping("/callback/{botName}")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Void> onUpdateReceived(
            @PathVariable String botName,
            @RequestBody BotUpdateDto botUpdate
    ) {
        return Mono.deferContextual(contextView -> {
            // 黑名单检查：命中直接丢弃，不发布事件
            Optional<Long> chatIdOpt = extractChatId(botUpdate);
            if (chatIdOpt.isPresent()) {
                String blacklistKey = "bot:blacklist:" + botName + ":" + chatIdOpt.get();
                return redisTemplate.hasKey(blacklistKey)
                        .flatMap(isBlacklisted -> {
                            if (Boolean.TRUE.equals(isBlacklisted)) {
                                return Mono.empty();
                            }
                            return LogUtils.processWebhookUpdateAndPublishEvent(
                                    contextView, eventPublisher, botName, botUpdate
                            );
                        });
            }
            return LogUtils.processWebhookUpdateAndPublishEvent(
                    contextView, eventPublisher, botName, botUpdate
            );
        }).doFinally(LogUtils::clearMDC);
    }
}
