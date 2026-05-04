package com.backend.bot.listener;

import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.dto.MessageDto;
import com.backend.bot.dto.UserDto;
import com.backend.bot.event.BotUpdateEvent;
import com.backend.bot.service.BotCoreService;
import com.backend.bot.service.BotUpdateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import filter.TraceIdFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.time.Duration;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import static com.backend.bot.util.BotChatUtils.extractChatId;

@Component
@Slf4j
public class BotUpdateListener {

    private final BotCoreService botCoreService;
    private final BotUpdateService botUpdateHandlerService;
    private final Scheduler blockingTaskScheduler;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final int MAX_UNAUTHORIZED_ATTEMPTS = 2;
    private static final Duration BLACKLIST_TTL = Duration.ofDays(30);

    public BotUpdateListener(
            BotCoreService botCoreService,
            BotUpdateService botUpdateHandlerService,

            @Qualifier("blockingTaskScheduler") Scheduler blockingTaskScheduler,
            ReactiveStringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {
        this.botCoreService = botCoreService;
        this.botUpdateHandlerService = botUpdateHandlerService;

        this.blockingTaskScheduler = blockingTaskScheduler;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @EventListener
    public void handleBotUpdateEvent(BotUpdateEvent event) {
        String botName = event.botName();
        BotUpdateDto botUpdate = event.botUpdate();
        final String traceId = event.traceId();

        Long chatId = extractChatId(botUpdate).orElse(null);
        Long userId = extractUserId(botUpdate);
        String userInfo = extractUserInfo(botUpdate);

        Mono<Void> processingPipeline = Mono.defer(() -> {
                    log.info("[traceId={}]📨 [AsyncListener] Processing event for bot: {} (ChatID: {})", traceId, botName, chatId);

                    return botCoreService.findByBotName(botName)
                            .timeout(Duration.ofSeconds(2), Mono.empty())
                            .onErrorResume(java.util.concurrent.TimeoutException.class, e -> {
                                log.warn("⚠️ BotConfigEntity lookup timed out for bot: {}", botName);
                                return Mono.empty();
                            });
                })
                .flatMap(botConfigEntity -> {
                    return Mono.just(botConfigEntity);
                })
                .filter(botConfigEntity -> {
                    if (botConfigEntity.getStatus() == null || botConfigEntity.getStatus() != 1) {
                        log.warn("⏸️ Bot {} is inactive. Ignoring update.", botName);
                        return false;
                    }
                    return true;
                })
                .flatMap(botConfigEntity -> {
                    if (chatId == null) {
                        log.debug("⚠️ No Chat ID found, skipping whitelist check for bot {}", botName);
                        return Mono.just(botConfigEntity);
                    }

                    return botCoreService.isChatIdAuthorized(botConfigEntity.getId(), botConfigEntity.getBotName(), chatId)
                            .flatMap(isAllowed -> {
                                if (Boolean.TRUE.equals(isAllowed)) {
                                    return Mono.just(botConfigEntity);
                                } else {
                                    return checkAndBlacklist(botName, userId, userInfo)
                                            .flatMap(blacklisted -> {
                                                if (blacklisted) {
                                                    log.warn("🚫 Rejected update for bot {} from BLACKLISTED user: {}", botName, userInfo);
                                                } else {
                                                    log.warn("⛔ Rejected update for bot {} from UNAUTHORIZED user: {}", botName, userInfo);
                                                }
                                                return Mono.empty();
                                            });
                                }
                            });
                })
                .flatMap(botConfigEntity -> {
                    String logMessage = "";
                    if (botUpdate.message() != null && botUpdate.message().text() != null) {
                        logMessage = String.format("Routing TEXT message (Length: %d)", botUpdate.message().text().length());
                    } else if (botUpdate.callbackQuery() != null) {
                        logMessage = "Routing CALLBACK query";
                    } else {
                        logMessage = "Routing OTHER update type";
                    }

                    log.info("[traceId={}]✅ Update passed filters. {} to BotUpdateHandlerService.",
                            traceId, logMessage);

                    return botUpdateHandlerService.handleUpdate(botConfigEntity, botUpdate);
                })
                .contextWrite(context -> {
                    if (traceId != null) {
                        return context.put(TraceIdFilter.CONTEXT_KEY_TRACE_ID, traceId);
                    }
                    return context;
                })
                .doOnError(e -> log.error("❌ Error in async listener for bot {}", botName, e))
                .onErrorResume(e -> Mono.empty())
                .then();

        processingPipeline
                .subscribeOn(blockingTaskScheduler)
                .subscribe();
    }

    private String extractUserInfo(BotUpdateDto update) {
        UserDto user = null;
        if (update.message() != null) {
            user = update.message().from();
        } else if (update.callbackQuery() != null) {
            user = update.callbackQuery().from();
        }
        if (user == null) return "userId=unknown, chatId=" + extractChatId(update).orElse(null);
        return String.format("userId=%d, username=%s, tgUsername=%s, chatId=%d",
                user.id(),
                user.firstName() != null ? user.firstName() : "",
                user.username() != null ? "@" + user.username() : "N/A",
                extractChatId(update).orElse(null));
    }

    /**
     * 检查未授权用户并拉黑：第一次警告，第二次起直接拉黑
     * @return true=已拉黑, false=首次未授权
     */
    private Long extractUserId(BotUpdateDto update) {
        com.backend.bot.dto.UserDto user = null;
        if (update.message() != null) user = update.message().from();
        else if (update.callbackQuery() != null) user = update.callbackQuery().from();
        return user != null ? user.id() : null;
    }

    private Mono<Boolean> checkAndBlacklist(String botName, Long userId, String userInfo) {
        String counterKey = "bot:unauthorized:" + botName + ":" + userId;
        String blacklistKey = "bot:blacklist:" + botName + ":" + userId;

        return redisTemplate.hasKey(blacklistKey)
                .flatMap(isBlacklisted -> {
                    if (Boolean.TRUE.equals(isBlacklisted)) {
                        return Mono.just(true);
                    }
                    return redisTemplate.opsForValue().increment(counterKey)
                            .flatMap(count -> {
                                if (count >= MAX_UNAUTHORIZED_ATTEMPTS) {
                                    log.warn("🔒 Auto-blacklisting for bot: {} | {}", botName, userInfo);
                                    // 存用户信息 JSON
                                    return redisTemplate.opsForValue().set(blacklistKey, userInfo, BLACKLIST_TTL)
                                            .thenReturn(true);
                                }
                                return redisTemplate.expire(counterKey, BLACKLIST_TTL).thenReturn(false);
                            });
                });
    }
}
