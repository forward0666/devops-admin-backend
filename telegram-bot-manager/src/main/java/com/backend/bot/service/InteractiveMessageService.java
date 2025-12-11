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
        
        // 创建组合的日志前缀，包含traceId和bot标识符
        final String combinedLogPrefix = logPrefix + botLogIdentifier;

        log.info("{}⏳ Scheduling auto-deletion for message {} in {}s. userId: {}", combinedLogPrefix, messageId, delaySeconds, userId);

        // 安排自动删除任务
        Disposable deletionTask = Mono.deferContextual(cv -> {
                    // 确保上下文正确传播到延迟任务
                    return Mono.delay(Duration.ofSeconds(delaySeconds), Schedulers.parallel())
                            .flatMap(aLong -> {
                                // 在每个操作前同步上下文到MDC
                                LogUtils.syncTraceIdToMDC(cv);

                                log.info("{}🕒 Auto-deletion task triggered for message {} after {}s", combinedLogPrefix, messageId, delaySeconds);

                                // 创建删除消息的操作链，确保每个步骤都传播上下文
                                return Mono.fromRunnable(() -> {
                                            log.warn("{}⏰ Auto-deleted message {} after {}s timeout.", combinedLogPrefix, messageId, delaySeconds);
                                        })
                                        .then(botClientService.deleteMessage(token, chatId, messageId))
                                        .doOnSuccess(v -> log.info("{}✅ Message {} successfully deleted", combinedLogPrefix, messageId))
                                        .onErrorResume(e -> {
                                            log.warn("❌ {} Failed to delete message {}. Already deleted or error: {}", combinedLogPrefix, messageId, e.getMessage());
                                            return Mono.empty(); // 失败也继续执行，让流正常完成
                                        });
                                // 移除：之前的会话清理逻辑，现在移到 doFinally(ON_COMPLETE) 确保执行
                            })
                            .doFinally(signalType -> {
                                // Resync MDC before logging in doFinally
                                LogUtils.syncTraceIdToMDC(cv);

                                if (signalType == reactor.core.publisher.SignalType.ON_COMPLETE) {
                                    // 任务成功完成 (消息被删除或删除失败但计时器流程走完)，现在清理 Disposable 引用
                                    log.info("{}🔒 Auto-deletion task finished with signal: {}. userId: {}", combinedLogPrefix, signalType, userId);
                                    
                                    // 只有当 userId 不为 0 时，才清理会话
                                    if (userId != 0L) {
                                        log.info("{}🗑️ Clearing session for user {} after message deletion.", combinedLogPrefix, userId);
                                        userSessionService.cancelPendingDeletion(userId)
                                                .then(userSessionService.clearUserSession(userId))
                                                .doOnSuccess(v -> log.info("{}✅ Cleared session for user {}", combinedLogPrefix, userId))
                                                .doOnError(e -> log.error("{}❌ Failed to clear session after ON_COMPLETE: {}", combinedLogPrefix, e.getMessage()))
                                                .contextWrite(cv)
                                                .subscribe();
                                    } else {
                                        // userId 为 0 表示这是系统消息，不需要清理会话
                                        log.info("{}🔒 System message deletion completed, no session cleanup needed (userId=0).", combinedLogPrefix);
                                        // 只需要取消待删除任务，不清理会话
                                        userSessionService.cancelPendingDeletion(userId)
                                                .contextWrite(cv)
                                                .subscribe();
                                    }

                                } else if (signalType == reactor.core.publisher.SignalType.ON_ERROR) {
                                    // 任务因错误终止时，可能需要清理会话
                                    log.error("{}🔒 Auto-deletion task failed with signal: {}.", combinedLogPrefix, signalType);
                                    
                                    // 只有当 userId 不为 0 时，才清理会话
                                    if (userId != 0L) {
                                        log.error("{}🔒 Forcibly clearing user session due to error.", combinedLogPrefix);
                                        userSessionService.clearUserSession(userId).contextWrite(cv).subscribe();
                                    } else {
                                        log.error("{}🔒 System message deletion failed, no session cleanup needed.", combinedLogPrefix);
                                    }
                                } else if (signalType == reactor.core.publisher.SignalType.CANCEL) {
                                    // 任务被取消 (新的用户交互触发的 dispose())。
                                    // 此时只需要记录日志，Session 的清理和重建由新的 Handler 负责。
                                    log.info("{}🔒 Auto-deletion task finished with signal: {}. Cancelling Disposable only, session cleanup deferred.", combinedLogPrefix, signalType);
                                }
                            });
                })
                // 将外部捕获的上下文写入这个响应式流
                .contextWrite(Context.of(contextView))
                .subscribe(
                        v -> log.info("{}✅ Auto-deletion task completed successfully", combinedLogPrefix),
                        e -> {
                            log.error("{}❌ Message deletion task failed for user {}: {}", combinedLogPrefix, userId, e.getMessage());
                            // 确保即使在任务订阅失败的情况下也清除用户会话
                            userSessionService.clearUserSession(userId).contextWrite(Context.of(contextView)).subscribe();
                        }
                );

        // 存储任务引用，以便用户交互时可以取消
        return userSessionService.storePendingDeletion(userId, deletionTask)
                .contextWrite(Context.of(contextView))
                .then();
    }
}