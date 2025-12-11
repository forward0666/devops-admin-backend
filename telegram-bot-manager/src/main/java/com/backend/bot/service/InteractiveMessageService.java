package com.backend.bot.service;
import com.backend.bot.util.LogUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context; // 🌟 导入 Context
import reactor.util.context.ContextView;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class InteractiveMessageService {

    private final BotClientService botClientService;
    private final UserSessionService userSessionService;

    /**
     * 安排一个消息的自动删除任务，并将任务引用存储到用户会话中。
     * ... (javadoc 不变)
     */
    public Mono<Void> scheduleMessageDeletion(
            String token,
            Long userId,
            Long chatId,
            Long messageId,
            int delaySeconds,
            String botLogIdentifier,
            ContextView contextView) {

        final String logPrefix = LogUtils.prepareMdcAndGetPrefix(contextView);

        log.info("{}{}⏳ Scheduling auto-deletion for message {} in {}s.", logPrefix, botLogIdentifier, messageId, delaySeconds);

        // 安排自动删除任务
        Disposable deletionTask = Mono.deferContextual(cv -> {
            // 确保上下文正确传播到延迟任务
            return Mono.delay(Duration.ofSeconds(delaySeconds), Schedulers.parallel())
                    .flatMap(aLong -> {
                        // 在每个操作前同步上下文到MDC
                        LogUtils.syncTraceIdToMDC(cv);
                        
                        log.info("{}{}🕒 Auto-deletion task triggered for message {} after {}s", logPrefix, botLogIdentifier, messageId, delaySeconds);
                        
                        // 创建删除消息的操作链，确保每个步骤都传播上下文
                        return Mono.fromRunnable(() -> {
                                    log.warn("{}{}⏰ Auto-deleted message {} after {}s timeout.", logPrefix, botLogIdentifier, messageId, delaySeconds);
                                })
                                .then(botClientService.deleteMessage(token, chatId, messageId))
                                .doOnSuccess(v -> log.info("{}{}✅ Message {} successfully deleted", logPrefix, botLogIdentifier, messageId))
                                .onErrorResume(e -> {
                                    log.warn("❌ {}{} Failed to delete message {}. Already deleted or error: {}", logPrefix, botLogIdentifier, messageId, e.getMessage());
                                    return Mono.empty(); // 失败也继续执行清理
                                })
                                // 无论消息删除是否成功，都执行清理操作
                                .then(userSessionService.cancelPendingDeletion(userId))
                                .doOnSuccess(v -> log.info("{}{}✅ Cancelled pending deletion task for user {}", logPrefix, botLogIdentifier, userId))
                                .then(userSessionService.clearUserSession(userId))
                                .doOnSuccess(v -> log.info("{}{}✅ Cleared session for user {}", logPrefix, botLogIdentifier, userId));
                    })
                    .doFinally(signalType -> {
                        // 确保无论如何都会清除用户会话
                        log.info("{}{}🔒 Auto-deletion task finished with signal: {}. Forcibly clearing user session.", logPrefix, botLogIdentifier, signalType);
                        userSessionService.clearUserSession(userId).contextWrite(cv).subscribe();
                    });
        })
        // 将外部捕获的上下文写入这个响应式流
        .contextWrite(Context.of(contextView))
        .subscribe(
                v -> log.info("{}{}✅ Auto-deletion task completed successfully", logPrefix, botLogIdentifier),
                e -> {
                    log.error("{}{}❌ Message deletion task failed for user {}: {}", logPrefix, botLogIdentifier, userId, e.getMessage());
                    // 确保即使在任务失败的情况下也清除用户会话
                    userSessionService.clearUserSession(userId).contextWrite(Context.of(contextView)).subscribe();
                }
        );

        // 存储任务引用，以便用户交互时可以取消
        return userSessionService.storePendingDeletion(userId, deletionTask)
                .contextWrite(Context.of(contextView))
                .then();
    }
}
