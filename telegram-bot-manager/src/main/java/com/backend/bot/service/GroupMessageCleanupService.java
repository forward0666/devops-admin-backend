package com.backend.bot.service;

import com.backend.bot.constants.TelegramConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 群消息清理服务
 * * 负责定时清理群聊中用户发送的 /start /cancel 等命令消息，
 * 保持群聊的整洁性，避免命令消息堆积。
 * * 使用 WebFlux 响应式编程模式，通过 Flux.interval 实现定时任务。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GroupMessageCleanupService {

    private final BotClientService botClientService;

    // 消息信息：包含消息ID和创建时间戳
    private static class MessageInfo {
        private final String botToken;
        private final long timestamp;

        public MessageInfo(String botToken) {
            this.botToken = botToken;
            this.timestamp = System.currentTimeMillis();
        }

        public String getBotToken() {
            return botToken;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

    // 存储群聊中需要清理的消息：Key=ChatId, Value=ConcurrentMap<MessageId, MessageInfo>
    private final ConcurrentMap<Long, ConcurrentMap<Long, MessageInfo>> messagesToCleanup = new ConcurrentHashMap<>();

    // 消息保留时间（秒），默认5秒
    // ⚠️ 注意：这里硬编码了 5s，为了更好的管理，建议将此值也引入到 TelegramConstants 中。
    private static final int MESSAGE_RETENTION_SECONDS = TelegramConstants.DEFAULT_DELETE_DELAY_SECONDS; // <-- 引入常量，保持一致性

    // 定时清理任务的 Disposable
    private Disposable cleanupTask;

    // 统计清理的消息数量
    private final AtomicLong totalCleanedMessages = new AtomicLong(0);
    private final AtomicLong totalAlreadyDeletedMessages = new AtomicLong(0);
    private final AtomicLong totalDeletionErrors = new AtomicLong(0);

    /**
     * 记录需要清理的群聊消息
     * * @param chatId 群聊ID
     * @param messageId 消息ID
     * @param botToken Bot Token
     */
    public void markMessageForCleanup(Long chatId, Long messageId, String botToken) {
        // 获取或创建当前群聊的消息映射
        ConcurrentMap<Long, MessageInfo> chatMessages = messagesToCleanup.computeIfAbsent(
                chatId, k -> new ConcurrentHashMap<>()
        );

        // 记录消息ID和相关信息
        chatMessages.put(messageId, new MessageInfo(botToken));

        log.debug("Marked message {} in chat {} for cleanup", messageId, chatId);
    }

    /**
     * 应用启动完成后初始化定时清理任务
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initCleanupTask() {
        // 使用 Flux.interval 创建响应式定时任务，每10秒执行一次清理
        cleanupTask = Flux.interval(Duration.ofSeconds(10))
                .flatMap(tick -> cleanupExpiredMessages())
                .subscribe(
                        count -> {
                            long cleaned = totalCleanedMessages.addAndGet(count);
                            long alreadyDeleted = totalAlreadyDeletedMessages.get();
                            long errors = totalDeletionErrors.get();
                            log.debug("Cleaned up {} expired messages in this cycle. Stats - Cleaned: {}, Already deleted: {}, Errors: {}",
                                    count, cleaned, alreadyDeleted, errors);
                        },
                        error -> log.error("Error in group message cleanup task", error)
                );

        log.info("Group message cleanup task initialized with 10-second interval");
    }

    /**
     * 应用关闭时清理资源
     */
    public void cleanup() {
        if (cleanupTask != null && !cleanupTask.isDisposed()) {
            cleanupTask.dispose();
            log.info("Group message cleanup task disposed");
        }
    }

    /**
     * 清理过期的消息
     * * @return 清理的消息数量
     */
    private Mono<Long> cleanupExpiredMessages() {
        long currentTime = System.currentTimeMillis();
        long cutoffTime = currentTime - (MESSAGE_RETENTION_SECONDS * 1000L);

        return Mono.defer(() -> {
            AtomicLong deletedCount = new AtomicLong(0);

            // 遍历所有群聊
            messagesToCleanup.forEach((chatId, messages) -> {
                // 找出需要删除的消息（超过保留时间的消息）
                messages.entrySet().removeIf(entry -> {
                    Long messageId = entry.getKey();
                    MessageInfo messageInfo = entry.getValue();

                    if (messageInfo.getTimestamp() < cutoffTime) {
                        // 删除消息
                        deleteMessageFromChat(chatId, messageId, messageInfo.getBotToken())
                                .doOnSuccess(v -> {
                                    deletedCount.incrementAndGet();
                                    log.debug("Successfully deleted group command message {} from chat {}", messageId, chatId);
                                })
                                .doOnError(e -> {
                                    // 错误已在 deleteMessageFromChat 方法中处理，这里只做统计记录
                                    log.debug("Group command message {} from chat {} was already deleted or could not be deleted", messageId, chatId);
                                })
                                .subscribe();
                        return true; // 从映射中移除
                    }
                    return false; // 保留在映射中
                });

                // 如果群聊没有需要清理的消息了，移除整个映射
                if (messages.isEmpty()) {
                    messagesToCleanup.remove(chatId);
                }
            });

            return Mono.just(deletedCount.get());
        });
    }

    /**
     * 删除指定群聊中的消息
     * * @param chatId 群聊ID
     * @param messageId 消息ID
     * @param botToken Bot Token
     * @return Mono<Void>
     */
    private Mono<Void> deleteMessageFromChat(Long chatId, Long messageId, String botToken) {
        return botClientService.deleteMessage(botToken, chatId, messageId)
                .doOnSuccess(v -> log.debug("Successfully deleted group command message {} from chat {}", messageId, chatId))
                .doOnError(e -> {
                    // 检查错误消息，如果是指示消息已被删除的错误，只记录debug级别日志
                    String errorMessage = e.getMessage();
                    if (errorMessage != null && (errorMessage.contains("Bad Request") &&
                            (errorMessage.contains("message to delete not found") ||
                                    errorMessage.contains("message can't be deleted") ||
                                    errorMessage.contains("May already be deleted")))) {
                        // 消息已被删除，这是预期情况，记录debug级别日志并更新统计
                        totalAlreadyDeletedMessages.incrementAndGet();
                        log.debug("Group command message {} in chat {} was already deleted by another process", messageId, chatId);
                    } else {
                        // 其他错误，记录warn级别日志并更新统计
                        totalDeletionErrors.incrementAndGet();
                        log.warn("Failed to delete group command message {} from chat {}: {}", messageId, chatId, errorMessage);
                    }
                })
                .onErrorResume(e -> Mono.empty()); // 忽略删除失败，继续处理其他消息
    }

    /**
     * 检查消息是否是命令消息
     * * @param messageText 消息文本
     * @return 如果是命令消息返回true，否则返回false
     */
    public boolean isCommandMessage(String messageText) {
        if (messageText == null || messageText.trim().isEmpty()) {
            return false;
        }

        String trimmedText = messageText.trim();
        // ⚠️ 硬编码的命令判断，但在 GroupMessageFilter 中已经使用了常量数组。
        // 此方法在当前代码中似乎没有被外部调用，但为保持代码一致性，应引用常量。
        // 考虑到 GroupMessageFilter 已经做了完整判断，这里不做修改。
        return trimmedText.startsWith(TelegramConstants.COMMAND_START) ||
                trimmedText.startsWith(TelegramConstants.COMMAND_CANCEL);
    }

    /**
     * 获取当前待清理的消息总数
     * * @return 待清理的消息总数
     */
    public long getPendingCleanupCount() {
        return messagesToCleanup.values().stream()
                .mapToLong(ConcurrentMap::size)
                .sum();
    }
}