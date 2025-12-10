package com.backend.bot.service;

import com.backend.bot.util.LogUtils; // 引入 LogUtils 来处理 Trace ID 提取
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.ContextView;

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
     * @param botLogIdentifier Bot的日志标识符 (例如: [u8_whitelist_bot_dev]，不含 Trace ID 前缀)
     * @param contextView 原始请求的 Reactor 上下文，包含 Trace ID
     * @return 用于链式调用的 Mono<Void>，实际任务在 subscribe() 后开始
     */
    public Mono<Void> scheduleMessageDeletion(
            String token,
            Long userId,
            Long chatId,
            Long messageId,
            int delaySeconds,
            String botLogIdentifier, // 假设这里只传入了 [u8_whitelist_bot_dev]
            ContextView contextView) {

        // 1. 内部构造包含 Trace ID 的完整日志前缀
        // 这是修复的关键：在服务内部获取 Trace ID 前缀并与传入的 Bot ID 结合，以保证日志完整性。
        final String logPrefix = LogUtils.prepareMdcAndGetPrefix(contextView);
        // 构造完整的日志标识符，例如: [traceId=9ab9720d2c357a4b-SIN][u8_whitelist_bot_dev]
        final String fullLogIdentifier = logPrefix + botLogIdentifier;

        // 初始调度日志 (在发起线程上)
        log.info("{}⏳ Scheduling auto-deletion for message {} in {}s.", fullLogIdentifier, messageId, delaySeconds);

        // 2. 安排自动删除任务
        Disposable deletionTask = Mono.delay(Duration.ofSeconds(delaySeconds))
                // 确保 ContextView 传递到并行调度器中
                .contextWrite(ctx -> ctx.putAll(contextView))
                .subscribeOn(Schedulers.parallel()) // 确保删除任务在并行调度器上运行
                .flatMap(aLong -> {
                    // 延迟触发日志 (在并行线程上)
                    log.warn("{}⏰Auto-deleting menu message {} after {}s timeout.", fullLogIdentifier, messageId, delaySeconds);

                    // 尝试删除消息，然后清理会话中的 Disposable 引用
                    return botClientService.deleteMessage(token, chatId, messageId)
                            .onErrorResume(e -> {
                                log.warn("❌ {} Failed to delete message {}. Already deleted or error: {}", fullLogIdentifier, messageId, e.getMessage());
                                return Mono.empty(); // 失败也继续执行清理
                            })
                            .then(userSessionService.cancelPendingDeletion(userId));
                })
                .subscribe(
                        null,
                        // 订阅错误日志
                        e -> log.error("{}❌ Message deletion task failed for user {}: {}", fullLogIdentifier, userId, e.getMessage())
                );

        // 3. 存储任务引用，以便用户交互时可以取消
        return userSessionService.storePendingDeletion(userId, deletionTask).then();
    }
}