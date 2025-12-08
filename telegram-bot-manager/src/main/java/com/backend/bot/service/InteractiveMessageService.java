package com.backend.bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

/**
 * 交互式消息服务。
 * 职责：封装消息的定时自动销毁逻辑，解决 StartCommandHandler 和 MenuNavigationHandler 中的代码重复。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InteractiveMessageService {

    private final BotClientService botClientService;
    private final UserSessionService userSessionService;

    /**
     * 安排一个消息的自动删除任务，并将任务引用存储到用户会话中。
     * * @param token Bot Token
     * @param userId 用户的 Telegram ID
     * @param chatId 聊天的 Telegram ID
     * @param messageId 要删除的消息 ID
     * @param delaySeconds 延迟删除的秒数
     * @param logIdentifier 日志标识符
     * @return 用于链式调用的 Mono<Void>，实际任务在 subscribe() 后开始
     */
    public Mono<Void> scheduleMessageDeletion(
            String token,
            Long userId,
            Long chatId,
            Long messageId,
            int delaySeconds,
            String logIdentifier) {

        log.info("⏳ {} Scheduling auto-deletion for message {} in {}s.", logIdentifier, messageId, delaySeconds);

        // 1. 安排自动删除任务
        Disposable deletionTask = Mono.delay(Duration.ofSeconds(delaySeconds))
                .flatMap(aLong -> {
                    log.warn("⏰ {} Auto-deleting menu message {} after {}s timeout.", logIdentifier, messageId, delaySeconds);
                    // 尝试删除消息，然后清理会话中的 Disposable 引用
                    return botClientService.deleteMessage(token, chatId, messageId)
                            .onErrorResume(e -> {
                                log.warn("❌ {} Failed to delete message {}. Already deleted or error: {}", logIdentifier, messageId, e.getMessage());
                                return Mono.empty(); // 失败也继续执行清理
                            })
                            .then(userSessionService.cancelPendingDeletion(userId));
                })
                // 确保删除任务在并行调度器上运行，不阻塞主线程
                .subscribeOn(Schedulers.parallel())
                .subscribe(
                        null,
                        e -> log.error("❌ {} Message deletion task failed for user {}: {}", logIdentifier, userId, e.getMessage())
                );

        // 2. 存储任务引用，以便用户交互时可以取消
        return userSessionService.storePendingDeletion(userId, deletionTask).then();
    }
}