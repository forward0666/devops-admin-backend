package com.backend.bot.controller;

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
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

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

    @PostMapping("/callback/{botName}")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Void> onUpdateReceived(
            @PathVariable String botName,
            @RequestBody BotUpdateDto botUpdate
    ) {
        return Mono.deferContextual(contextView -> {
            // 提取用户和聊天信息（一次性赋值，确保 effectively final）
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
                return LogUtils.processWebhookUpdateAndPublishEvent(
                        contextView, eventPublisher, botName, botUpdate
                );
            }

            final String blacklistKey = "bot:blacklist:" + botName + ":" + user.id();
            final boolean isPrivate = chatId != null && chatId.equals(user.id());

            return redisTemplate.hasKey(blacklistKey)
                    .flatMap(isBlacklisted -> {
                        if (Boolean.TRUE.equals(isBlacklisted)) {
                            if (isPrivate) {
                                return Mono.empty();
                            }
                            return botCoreService.findByBotName(botName)
                                    .flatMap(bot -> botClientService.sendMessage(
                                            bot.getBotToken(), chatId,
                                            "⚠️ 您已在黑名单中，无法使用该 Bot。\n如需解封请联系管理员。"
                                    ))
                                    .then(Mono.empty());
                        }
                        return LogUtils.processWebhookUpdateAndPublishEvent(
                                contextView, eventPublisher, botName, botUpdate
                        );
                    });
        }).doFinally(LogUtils::clearMDC);
    }
}
