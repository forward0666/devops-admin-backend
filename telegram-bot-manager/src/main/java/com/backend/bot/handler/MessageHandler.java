package com.backend.bot.handler.impl;

import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.dto.InlineKeyboardMarkupDto;
import com.backend.bot.entity.BotConfigEntity;
import com.backend.bot.handler.UpdateHandler;
import com.backend.bot.service.BotClientService;
import com.backend.bot.template.TelegramMarkup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class MessageHandler implements UpdateHandler {

    private final BotClientService botClientService;

    @Override
    public boolean support(BotUpdateDto update) {
        return update.message() != null && update.message().text() != null;
    }

    @Override
    public Mono<Void> handle(BotConfigEntity botEntity, BotUpdateDto botUpdate) {
        String token = botEntity.getBotToken();
        String botName = botEntity.getBotName();
        String botType = botEntity.getBotType().getDbValue();
        String logIdentifier = String.format("[%s]", botName);

        String text = botUpdate.message().text();
        Long chatId = botUpdate.message().chat().id();
        String type = botUpdate.message().chat().type();

        // 1. --- 仅响应 /start 命令 ---
        if (text != null && text.startsWith("/start")) {
            log.info("✅ {} Received /start command in {} chat. Chat ID: {}", logIdentifier, type, chatId);

            // 动态生成内联键盘
            InlineKeyboardMarkupDto replyMarkup = TelegramMarkup.createDynamicKeyboard(botType);
            String responseText = "欢迎使用！请从下方按钮中选择您需要的服务：";

            if (replyMarkup == null) {
                responseText = String.format("欢迎！机器人类型 [%s] 无法识别，请联系管理员。", botType);
            }

            log.info("✅ {} STAGE 3: Preparing to send response message with keyboard (Type: {}).", logIdentifier, botType);

            return botClientService.sendMessage(token, chatId, responseText, replyMarkup)
                    .onErrorResume(e -> {
                        log.error("❌ Failed to send START message for bot {}. Error: {}", logIdentifier, e.getMessage());
                        return Mono.empty();
                    })
                    .then();
        }

        // 2. --- /status 命令处理 ---
        else if ("private".equals(type) && text.startsWith("/status")) {
            String responseText = "Bot Status: Active (Chat ID: " + chatId + ")";
            return botClientService.sendMessage(token, chatId, responseText, null)
                    .onErrorResume(e -> Mono.empty())
                    .then();
        }

        // 3. --- 忽略其他所有消息 ---
        log.debug("Skipping message update (Type: {}): {}", type, text);
        return Mono.empty();
    }
}