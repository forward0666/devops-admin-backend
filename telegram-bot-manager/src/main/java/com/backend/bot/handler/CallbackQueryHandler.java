package com.backend.bot.handler.impl;

import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.entity.BotConfigEntity;
import com.backend.bot.handler.UpdateHandler;
import com.backend.bot.service.BotClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class CallbackQueryHandler implements UpdateHandler {

    private final BotClientService botClientService;

    @Override
    public boolean support(BotUpdateDto update) {
        return update.callbackQuery() != null;
    }

    @Override
    public Mono<Void> handle(BotConfigEntity botEntity, BotUpdateDto botUpdate) {
        String token = botEntity.getBotToken();
        String botName = botEntity.getBotName();
        String botType = botEntity.getBotType();
        String logIdentifier = String.format("[%s]", botName);

        String callbackData = botUpdate.callbackQuery().data();
        Long chatId = botUpdate.callbackQuery().message().chat().id();
        String callbackQueryId = botUpdate.callbackQuery().id();

        log.info("⚙️ {} Received callback query: {}", logIdentifier, callbackData);

        // 1.1 立即响应 callback_query 以停止按钮上的加载动画 (Fire-and-forget)
        botClientService.answerCallbackQuery(token, callbackQueryId, "已接收请求: " + callbackData)
                .subscribe(
                        null,
                        e -> log.error("❌ Failed to answer callback query for bot {}. Error: {}", logIdentifier, e.getMessage())
                );

        // 1.2 根据 callbackData 执行业务逻辑
        String responseText = "您点击了: " + callbackData + "。 Bot类型: " + botType;

        // 1.3 异步发送响应消息
        return botClientService.sendMessage(token, chatId, responseText, null)
                .onErrorResume(e -> {
                    log.error("❌ Failed to send response to callback query for bot {}. Error: {}", logIdentifier, e.getMessage());
                    return Mono.empty();
                })
                .then();
    }
}