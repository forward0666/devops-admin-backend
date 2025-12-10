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
                    // 🌟 关键修复：在 flatMap 内部，我们创建一个新的 Mono 链，
                    // 并使用 .contextWrite() 将捕获的 contextView 注入进去。
                    // 这样，后续的 botClientService.deleteMessage() 就能访问到正确的上下文。
                    return Mono.fromRunnable(() -> {
                                log.warn("{}⏰ Auto-deleted message {} after {}s timeout.", fullLogIdentifier, messageId, delaySeconds);
                            })
                            .then(botClientService.deleteMessage(token, chatId, messageId))
                            .onErrorResume(e -> {
                                log.warn("❌ {} Failed to delete message {}. Already deleted or error: {}", fullLogIdentifier, messageId, e.getMessage());
                                return Mono.empty(); // 失败也继续执行清理
                            })
                            .then(userSessionService.cancelPendingDeletion(userId))
                            // 🌟 将捕获的外部上下文写入到这个新的内部响应式链中
                            .contextWrite(Context.of(contextView));
                })
                .subscribe(
                        null,
                        e -> log.error("{}❌ Message deletion task failed for user {}: {}", fullLogIdentifier, userId, e.getMessage())
                );

        // 存储任务引用，以便用户交互时可以取消
        return userSessionService.storePendingDeletion(userId, deletionTask).then();
    }
}
