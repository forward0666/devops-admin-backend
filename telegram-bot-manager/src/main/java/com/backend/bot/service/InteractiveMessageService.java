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

        double score = System.currentTimeMillis() / 1000.0 + delaySeconds;
        String value = chatId + ":" + messageId + ":" + userId + ":" + token;

        log.info("{}⏳ [Step1] 准备写入Redis ZSET | chatId={}, messageId={}, userId={}, delay={}s, value长度={}, score={}",
                combinedLogPrefix, chatId, messageId, userId, delaySeconds, value.length(), score);

        return Mono.fromRunnable(() -> {
                    Boolean added = stringRedisTemplate.opsForZSet().add(ZSET_KEY, value, score);
                    log.info("{}✅ [Step2] Redis ZSET写入完成 | added={}, key={}, score={}", combinedLogPrefix, added, ZSET_KEY, score);
                    Long size = stringRedisTemplate.opsForZSet().size(ZSET_KEY);
                    log.info("{}📊 [Step3] Redis ZSET当前任务数={}", combinedLogPrefix, size);
                })
                .doOnSuccess(v -> log.info("{}✅ [scheduleMessageDeletion] 完成 | messageId={}, delay={}s", combinedLogPrefix, messageId, delaySeconds))
                .onErrorResume(e -> {
                    log.error("{}❌ [Step2] Redis ZSET写入失败 | error={}", combinedLogPrefix, e.getMessage(), e);
                    return Mono.empty();
                })
                .then();
    }

    /**
     * 取消指定消息的删除任务
     */
    public void cancelPendingDeletion(Long chatId, Long messageId) {
        String prefix = chatId + ":" + messageId + ":";
        log.info("🗑️ [cancelPendingDeletion] 开始取消 | chatId={}, messageId={}, prefix={}", chatId, messageId, prefix);
        Set<String> members = stringRedisTemplate.opsForZSet().rangeByValue(ZSET_KEY, prefix, prefix + "\uffff");
        if (members != null && !members.isEmpty()) {
            Long removed = stringRedisTemplate.opsForZSet().remove(ZSET_KEY, members.toArray());
            log.info("✅ [cancelPendingDeletion] 已取消 {} 个任务 | messageId={}", removed, messageId);
        } else {
            log.info("📭 [cancelPendingDeletion] 未找到待删除任务 | messageId={}", messageId);
        }
    }

    /**
     * 定时扫描 ZSET，删除过期消息。每 3 秒执行一次。
     */
    @Scheduled(fixedRate = 3000)
    public void processPendingDeletions() {
        try {
            double now = System.currentTimeMillis() / 1000.0;
            Long totalSize = stringRedisTemplate.opsForZSet().size(ZSET_KEY);

            Set<ZSetOperations.TypedTuple<String>> expired = stringRedisTemplate.opsForZSet()
                    .rangeByScoreWithScores(ZSET_KEY, 0, now);

            if (expired == null || expired.isEmpty()) {
                log.debug("📭 [ScheduledTask] 无过期任务 | ZSET总数={}", totalSize);
                return;
            }

            log.info("🔍 [ScheduledTask] 发现 {} 个过期任务(共{}个) | 开始处理...", expired.size(), totalSize);

            for (ZSetOperations.TypedTuple<String> tuple : expired) {
                String value = tuple.getValue();
                if (value == null) continue;
                log.info("📋 [ScheduledTask] 处理任务 | value长度={}, score={}", value.length(), tuple.getScore());
                processDeletionTask(value);
            }

            Long removed = stringRedisTemplate.opsForZSet().removeRangeByScore(ZSET_KEY, 0, now);
            log.info("✅ [ScheduledTask] 清理完成 | 删除了 {} 个过期项, ZSET剩余={}", removed,
                    stringRedisTemplate.opsForZSet().size(ZSET_KEY));
        } catch (Exception e) {
            log.error("❌ [ScheduledTask] 执行失败", e);
        }
    }

    private void processDeletionTask(String value) {
        try {
            String[] parts = value.split(":", 4);
            if (parts.length < 4) {
                log.warn("⚠️ [processDeletion] 格式错误(字段数<4) | value={}", value);
                return;
            }

            long chatId = Long.parseLong(parts[0]);
            long messageId = Long.parseLong(parts[1]);
            long userId = Long.parseLong(parts[2]);
            String token = parts[3];

            log.info("🗑️ [processDeletion] 开始删除 | chatId={}, messageId={}, userId={}", chatId, messageId, userId);

            botClientService.deleteMessage(token, chatId, messageId)
                    .doOnSuccess(v -> {
                        log.info("✅ [processDeletion] 删除成功 | chatId={}, messageId={}, userId={}", chatId, messageId, userId);
                        if (userId != 0L) {
                            log.info("🔄 [processDeletion] 清理session | userId={}", userId);
                            userSessionService.clearUserSession(userId).subscribe();
                        }
                    })
                    .onErrorResume(e -> {
                        log.warn("⚠️ [processDeletion] 删除失败(可能已删除) | chatId={}, messageId={}, error={}", chatId, messageId, e.getMessage());
                        if (userId != 0L) {
                            userSessionService.clearUserSession(userId).subscribe();
                        }
                        return Mono.empty();
                    })
                    .subscribe();
        } catch (Exception e) {
            log.error("❌ [processDeletion] 解析/执行失败 | value={}", value, e);
        }
    }
}
