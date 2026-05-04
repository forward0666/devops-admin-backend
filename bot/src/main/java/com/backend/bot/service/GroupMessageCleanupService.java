package com.backend.bot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ZSetCommands;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.time.Duration;
import java.util.Set;

/**
 * 群消息清理服务 — Redis ZSET + @Scheduled
 * 和 InteractiveMessageService 一样的模式，用 Redis ZSET 存过期消息，定时扫描删除。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GroupMessageCleanupService {

    private static final String ZSET_KEY = "bot:groupPendingDeletion";
    private static final int SCAN_BATCH = 100;

    private final ReactiveStringRedisTemplate redisTemplate;
    private final BotClientService botClientService;
    private final Scheduler blockingTaskScheduler;
    private final ObjectMapper objectMapper;

    /**
     * 标记消息待删除
     */
    public Mono<Void> markMessageForCleanup(Long chatId, Long messageId, String botToken) {
        long expiry = System.currentTimeMillis() + 5000; // 5s 后删除
        String value = chatId + ":" + messageId + ":" + botToken;

        return redisTemplate.opsForZSet()
                .add(ZSET_KEY, value, expiry)
                .subscribeOn(blockingTaskScheduler)
                .then()
                .doOnSuccess(v -> log.debug("[GroupCleanup] Marked message {} in chat {} for cleanup", messageId, chatId))
                .onErrorResume(e -> {
                    log.error("[GroupCleanup] Failed to mark message {} in chat {}", messageId, chatId, e);
                    return Mono.empty();
                });
    }

    /**
     * 每 3 秒扫描一次，删除过期消息
     */
    @Scheduled(fixedRate = 3000)
    public void processPendingDeletions() {
        long now = System.currentTimeMillis();
        Set<String> expired = redisTemplate.opsForZSet()
                .rangeByScore(ZSET_KEY, 0, now)
                .subscribeOn(blockingTaskScheduler)
                .collectList()
                .block(Duration.ofSeconds(5));

        if (expired == null || expired.isEmpty()) {
            return;
        }

        log.debug("[GroupCleanup] Found {} expired message(s)", expired.size());

        for (String value : expired) {
            try {
                String[] parts = value.split(":", 3);
                if (parts.length < 3) {
                    redisTemplate.opsForZSet().remove(ZSET_KEY, value).block(Duration.ofSeconds(2));
                    continue;
                }
                long chatId = Long.parseLong(parts[0]);
                long messageId = Long.parseLong(parts[1]);
                String botToken = parts[2];

                botClientService.deleteMessage(botToken, chatId, messageId)
                        .doOnSuccess(v -> log.debug("[GroupCleanup] Deleted message {} from chat {}", messageId, chatId))
                        .doOnError(e -> {
                            String msg = e.getMessage();
                            if (msg != null && msg.contains("message to delete not found")) {
                                log.debug("[GroupCleanup] Message {} already deleted in chat {}", messageId, chatId);
                            } else {
                                log.warn("[GroupCleanup] Failed to delete message {} from chat {}: {}", messageId, chatId, msg);
                            }
                        })
                        .onErrorResume(e -> Mono.empty())
                        .subscribe();
            } catch (Exception e) {
                log.error("[GroupCleanup] Error processing: {}", value, e);
                redisTemplate.opsForZSet().remove(ZSET_KEY, value).block(Duration.ofSeconds(2));
            }
        }

        // 批量移除已处理的
        redisTemplate.opsForZSet().removeRangeByScore(ZSET_KEY, 0, now)
                .subscribeOn(blockingTaskScheduler)
                .subscribe();
    }

    public Mono<Long> getPendingCount() {
        return redisTemplate.opsForZSet().size(ZSET_KEY);
    }
}
