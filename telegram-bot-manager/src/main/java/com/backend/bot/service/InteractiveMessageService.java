package com.backend.bot.service;

import com.backend.bot.util.LogUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import java.util.Set;

/**
 * 消息自动删除服务
 *
 * 使用 Redis ZSET 存储待删除消息，定时任务扫描执行删除。
 * 优势：
 * 1. 程序重启后不丢失待删除任务
 * 2. 每条消息独立管理，互不影响
 * 3. 不占用应用线程，由统一的定时任务处理
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InteractiveMessageService {

    private final BotClientService botClientService;
    private final UserSessionService userSessionService;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String ZSET_KEY = "bot:pendingDeletion";

    public Mono<Void> scheduleMessageDeletion(
            String token,
            Long userId,
            Long chatId,
            Long messageId,
            int delaySeconds,
            String botLogIdentifier,
            ContextView contextView) {

        final String logPrefix = LogUtils.prepareMdcAndGetPrefix(contextView);
        final String combinedLogPrefix = logPrefix + botLogIdentifier;

        log.info("{}⏳ Scheduling auto-deletion for message {} in {}s. userId: {}", combinedLogPrefix, messageId, delaySeconds, userId);

        double score = System.currentTimeMillis() / 1000.0 + delaySeconds;
        String value = chatId + ":" + messageId + ":" + userId + ":" + token;

        return Mono.fromRunnable(() ->
                        stringRedisTemplate.opsForZSet().add(ZSET_KEY, value, score)
        )
        .onErrorResume(e -> {
            log.error("{}❌ Failed to store deletion task: {}", combinedLogPrefix, e.getMessage());
            return Mono.empty();
        })
        .then();
    }

    /**
     * 取消指定消息的删除任务
     */
    public void cancelPendingDeletion(Long chatId, Long messageId) {
        String prefix = chatId + ":" + messageId + ":";
        Set<String> members = stringRedisTemplate.opsForZSet().rangeByValue(ZSET_KEY, prefix, prefix + "\uffff");
        if (members != null && !members.isEmpty()) {
            stringRedisTemplate.opsForZSet().remove(ZSET_KEY, members.toArray());
            log.debug("🗑️ Cancelled {} pending deletion tasks for message {}", members.size(), messageId);
        }
    }

    /**
     * 定时扫描 ZSET，删除过期消息。每 3 秒执行一次。
     */
    @Scheduled(fixedRate = 3000)
    public void processPendingDeletions() {
        try {
            double now = System.currentTimeMillis() / 1000.0;
            Set<ZSetOperations.TypedTuple<String>> expired = stringRedisTemplate.opsForZSet()
                    .rangeByScoreWithScores(ZSET_KEY, 0, now);

            if (expired == null || expired.isEmpty()) {
                return;
            }

            for (ZSetOperations.TypedTuple<String> tuple : expired) {
                String value = tuple.getValue();
                if (value == null) continue;
                processDeletionTask(value);
            }

            stringRedisTemplate.opsForZSet().removeRangeByScore(ZSET_KEY, 0, now);
        } catch (Exception e) {
            log.error("❌ Error in processPendingDeletions", e);
        }
    }

    private void processDeletionTask(String value) {
        try {
            String[] parts = value.split(":", 4);
            if (parts.length < 4) {
                log.warn("⚠️ Invalid deletion task format: {}", value);
                return;
            }

            long chatId = Long.parseLong(parts[0]);
            long messageId = Long.parseLong(parts[1]);
            long userId = Long.parseLong(parts[2]);
            String token = parts[3];

            botClientService.deleteMessage(token, chatId, messageId)
                    .doOnSuccess(v -> {
                        log.info("✅ Auto-deleted message {} in chat {}", messageId, chatId);
                        if (userId != 0L) {
                            userSessionService.clearUserSession(userId).subscribe();
                        }
                    })
                    .onErrorResume(e -> {
                        log.warn("⚠️ Failed to delete message {} in chat {}: {}", messageId, chatId, e.getMessage());
                        if (userId != 0L) {
                            userSessionService.clearUserSession(userId).subscribe();
                        }
                        return Mono.empty();
                    })
                    .subscribe();
        } catch (Exception e) {
            log.error("❌ Failed to process deletion task: {}", value, e);
        }
    }
}
