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
        final String fullLogIdentifier = logPrefix + botLogIdentifier;

        log.info("{}⏳ Scheduling auto-deletion for message {} in {}s.", fullLogIdentifier, messageId, delaySeconds);

        // 安排自动删除任务
        Disposable deletionTask = Mono.delay(Duration.ofSeconds(delaySeconds), Schedulers.parallel())
                .flatMap(aLong -> {
                    log.info("{}🕒 Auto-deletion task triggered for message {} after {}s", fullLogIdentifier, messageId, delaySeconds);
                    
                    // 🌟 关键修复：在 flatMap 内部，我们创建一个新的 Mono 链，
                    // 并使用 .contextWrite() 将捕获的 contextView 注入进去。
                    // 这样，后续的 botClientService.deleteMessage() 就能访问到正确的上下文。
                    return Mono.fromRunnable(() -> {
                                log.warn("{}⏰ Auto-deleted message {} after {}s timeout.", fullLogIdentifier, messageId, delaySeconds);
                            })
                            .then(botClientService.deleteMessage(token, chatId, messageId))
                            .doOnSuccess(v -> log.info("{}✅ Message {} successfully deleted", fullLogIdentifier, messageId))
                            .onErrorResume(e -> {
                                log.warn("❌ {} Failed to delete message {}. Already deleted or error: {}", fullLogIdentifier, messageId, e.getMessage());
                                return Mono.empty(); // 失败也继续执行清理
                            })
                            // 无论消息删除是否成功，都执行清理操作
                            .then(userSessionService.cancelPendingDeletion(userId))
                            .doOnSuccess(v -> log.info("{}✅ Cancelled pending deletion task for user {}", fullLogIdentifier, userId))
                            .then(userSessionService.clearUserSession(userId))
                            .doOnSuccess(v -> log.info("{}✅ Cleared session for user {}", fullLogIdentifier, userId))
                            // 🌟 将捕获的外部上下文写入到这个新的内部响应式链中
                            .contextWrite(Context.of(contextView))
                            // 确保最终返回Mono<Void>
                            .then();
                })
                .doFinally(signalType -> {
                    // 确保无论如何都会清除用户会话
                    log.info("{}🔒 Auto-deletion task finished with signal: {}. Forcibly clearing user session.", fullLogIdentifier, signalType);
                    userSessionService.clearUserSession(userId).contextWrite(Context.of(contextView)).subscribe();
                })
                .subscribe(
                        v -> log.info("{}✅ Auto-deletion task completed successfully", fullLogIdentifier),
                        e -> {
                            log.error("{}❌ Message deletion task failed for user {}: {}", fullLogIdentifier, userId, e.getMessage());
                            // 确保即使在任务失败的情况下也清除用户会话
                            userSessionService.clearUserSession(userId).contextWrite(Context.of(contextView)).subscribe();
                        }
                );

        // 存储任务引用，以便用户交互时可以取消
        return userSessionService.storePendingDeletion(userId, deletionTask).then();
    }
}
