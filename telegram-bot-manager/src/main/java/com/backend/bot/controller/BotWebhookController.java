package com.backend.bot.controller;

import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.dto.CallbackQueryDto;
import com.backend.bot.dto.MessageDto;
import com.backend.bot.dto.UserDto;
import com.backend.bot.service.BotClientService;
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
            // 提取用户 ID 和 chatId
            UserDto user = null;
            Long chatId = null;
            if (botUpdate.message() != null) {
                user = botUpdate.message().from();
                chatId = botUpdate.message().chat().id();
            } else if (botUpdate.callbackQuery() != null) {
                user = botUpdate.callbackQuery().from();
                chatId = botUpdate.callbackQuery().message().chat().id();
            }

            if (user == null || user.id() == null) {
                return LogUtils.processWebhookUpdateAndPublishEvent(
                        contextView, eventPublisher, botName, botUpdate
                );
            }

            String blacklistKey = "bot:blacklist:" + botName + ":" + user.id();
            boolean isPrivate = chatId != null && chatId.equals(user.id());

            return redisTemplate.hasKey(blacklistKey)
                    .flatMap(isBlacklisted -> {
                        if (Boolean.TRUE.equals(isBlacklisted)) {
                            if (isPrivate) {
                                // 私聊：静默丢弃
                                return Mono.empty();
                            }
                            // 群聊：回复黑名单提示
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
