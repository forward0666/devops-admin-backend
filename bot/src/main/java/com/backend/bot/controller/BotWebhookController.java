package com.backend.bot.controller;
import com.backend.bot.service.InteractiveMessageService;

import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.dto.CallbackQueryDto;
import com.backend.bot.dto.MessageDto;
import com.backend.bot.dto.UserDto;
import com.backend.bot.service.BotCoreService;
import com.backend.bot.service.BotClientService;
import com.backend.bot.util.LogUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static com.backend.bot.util.BotChatUtils.extractChatId;

@RestController
@RequiredArgsConstructor
@Slf4j
public class BotWebhookController {

    private final ApplicationEventPublisher eventPublisher;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final BotCoreService botCoreService;
    private final BotClientService botClientService;
    private final InteractiveMessageService interactiveMessageService;

    @PostMapping("/callback/{botName}")
    public Mono<ResponseEntity<Map<String, String>>> onUpdateReceived(
            @PathVariable String botName,
            @RequestBody BotUpdateDto botUpdate
    ) {
        String updateType = botUpdate.message() != null ? "TEXT:" + botUpdate.message().text() :
                botUpdate.callbackQuery() != null ? "CALLBACK:" + botUpdate.callbackQuery().data() : "UNKNOWN";
        log.info("📨 [WebhookController] 收到请求 | botName={}, updateType={}", botName, updateType);

        // 立即返回 200，异步处理业务逻辑
        return Mono.just(ResponseEntity.ok(Map.of("status", "ok")))
                .doOnNext(response -> {
                    // 异步处理，不阻塞响应
                    processAsync(botName, botUpdate);
                });
    }

    private void processAsync(String botName, BotUpdateDto botUpdate) {
        Mono.defer(() -> {
                    final UserDto user;
                    final Long chatId;
                    if (botUpdate.message() != null) {
                        user = botUpdate.message().from();
                        chatId = botUpdate.message().chat().id();
                    } else if (botUpdate.callbackQuery() != null) {
                        user = botUpdate.callbackQuery().from();
                        chatId = botUpdate.callbackQuery().message().chat().id();
                    } else {
                        user = null;
                        chatId = null;
                    }

                    if (user == null || user.id() == null) {
                        LogUtils.processWebhookUpdateAndPublishEvent(
                                reactor.util.context.Context.empty(), eventPublisher, botName, botUpdate
                        );
                        return Mono.empty();
                    }

                    final String blacklistKey = "bot:blacklist:" + botName + ":" + user.id();
                    final boolean isPrivate = chatId != null && chatId.equals(user.id());

                    return redisTemplate.hasKey(blacklistKey)
                            .timeout(Duration.ofSeconds(3))
                            .onErrorResume(e -> {
                                log.error("❌ [WebhookController] Redis超时/异常，直接发布事件 | botName={}, error={}", botName, e.getMessage());
                                return Mono.just(false);
                            })
                            .flatMap(isBlacklisted -> {
                                if (Boolean.TRUE.equals(isBlacklisted)) {
                                    if (isPrivate) {
                                        log.debug("🔒 BLACKLISTED private chat: botName={}, userId={}", botName, user.id());
                                        return Mono.empty();
                                    }
                                    return botCoreService.findByBotName(botName)
                                                    .timeout(Duration.ofSeconds(3))
                                            .flatMap(bot -> {
                                                final Long msgId = extractMessageId(botUpdate);
                                                String warnMsg = String.format(
                                                        "🚫 %s (%s) 已在黑名单中，如需解封请联系管理员。",
                                                        user.firstName() != null ? user.firstName() : "",
                                                        user.username() != null ? "@" + user.username() : "N/A");
                                                Mono<Void> sendWarning = botClientService.sendMenuMessageWithResponse(
                                                        bot.getBotToken(), chatId, warnMsg, null
                                                ).flatMap(respJson -> {
                                                    try {
                                                        com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(respJson);
                                                        Long warnMsgId = root.path("result").path("message_id").asLong(0);
                                                        if (warnMsgId != 0) {
                                                            return interactiveMessageService.scheduleMessageDeletion(
                                                                    bot.getBotToken(), 0L, chatId, warnMsgId, 5, null, reactor.util.context.Context.empty()
                                                            );
                                                        }
                                                    } catch (Exception ignored) {}
                                                    return Mono.empty();
                                                }).onErrorResume(e -> Mono.empty());
                                                Mono<Void> deleteMsg = msgId != null
                                                        ? botClientService.deleteMessage(bot.getBotToken(), chatId, msgId)
                                                        : Mono.empty();
                                                return sendWarning.timeout(Duration.ofSeconds(3)).then(deleteMsg.timeout(Duration.ofSeconds(3)));
                                            })
                                            .then(Mono.empty());
                                }
                                return Mono.<Void>fromRunnable(() ->
                                    LogUtils.processWebhookUpdateAndPublishEvent(
                                        reactor.util.context.Context.empty(), eventPublisher, botName, botUpdate)
                                ).subscribeOn(Schedulers.boundedElastic()).then();
                            });
                })
                .timeout(Duration.ofSeconds(10))
                .doOnError(e -> log.error("❌ [WebhookController] 异步处理异常 | botName={}, error={}", botName, e.getMessage()))
                .onErrorResume(e -> Mono.empty())
                .doFinally(LogUtils::clearMDC)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }

    private Long extractMessageId(BotUpdateDto update) {
        if (update.message() != null) return update.message().messageId();
        if (update.callbackQuery() != null) return update.callbackQuery().message().messageId();
        return null;
    }
}
