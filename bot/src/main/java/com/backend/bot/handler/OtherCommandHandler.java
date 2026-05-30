package com.backend.bot.handler;

import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.entity.BotConfigEntity;
import com.backend.bot.service.BotClientService;
import com.backend.bot.service.InteractiveMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 负责处理非 /start 的所有其他命令，以及普通文本消息。
 * 优先级低于 StartCommandHandler (1)，高于 FallbackHandler (例如 99)。
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(10)
public class OtherCommandHandler implements UpdateHandler {

    private final BotClientService botClientService;
    private final InteractiveMessageService interactiveMessageService;

    @Override
    public boolean support(BotUpdateDto update) {
        if (update.message() != null && update.message().text() != null) {
            String text = update.message().text().trim();

            // 🌟 1. 检查是否为命令 (以 / 开头)
            if (text.startsWith("/")) {
                // 🌟 2. 排除 /start 命令，将 /start 留给 StartCommandHandler 处理
                return !text.startsWith("/start");
            }

            // 默认情况下，这个处理器也可以处理普通文本，但通常会将其留给优先级更低的 FallbackHandler 或 TextUpdateHandler
            // 假设它只处理命令，直到有特定业务需求。
        }
        return false;
    }

    @Override
    public Mono<Void> handle(BotConfigEntity botEntity, BotUpdateDto botUpdate) {
        String token = botEntity.getBotToken();
        String botName = botEntity.getBotName();
        String text = botUpdate.message().text();
        Long chatId = botUpdate.message().chat().id();
        String type = botUpdate.message().chat().type();

        // --- 仅处理非 /start 的命令 ---
        if (text != null && text.startsWith("/")) {
            // 这是处理 /help, /settings 等其他命令的地方
            log.info("ℹ️ Received unsupported command: {} for bot: {} in {} chat.", text, botName, type);

            String responseText = String.format("抱歉，命令 `%s` 暂未实现。请使用 `/start` 或通过菜单操作。", text);
            Long userId = botUpdate.message().from() != null ? botUpdate.message().from().id() : null;
            Long userMsgId = botUpdate.message().messageId();
            boolean isGroup = chatId < 0;

            return botClientService.sendMenuMessageWithResponse(token, chatId, responseText, null)
                    .flatMap(respJson -> {
                        try {
                            com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(respJson);
                            Long respMsgId = root.path("result").path("message_id").asLong(0);
                            if (respMsgId != 0) {
                                // 回复消息 5s 后删除
                                interactiveMessageService.scheduleMessageDeletion(token, userId, chatId, respMsgId, 5, null, reactor.util.context.Context.of(com.backend.bot.util.LogUtils.TRACE_ID_KEY, org.slf4j.MDC.get(com.backend.bot.util.LogUtils.TRACE_ID_KEY))).subscribe();
                            }
                        } catch (Exception ignored) {}
                        // 群聊中用户的命令消息也删除
                        if (isGroup && userMsgId != null && userId != null) {
                            interactiveMessageService.scheduleMessageDeletion(token, userId, chatId, userMsgId, 5, null, reactor.util.context.Context.of(com.backend.bot.util.LogUtils.TRACE_ID_KEY, org.slf4j.MDC.get(com.backend.bot.util.LogUtils.TRACE_ID_KEY))).subscribe();
                        }
                        return Mono.<Void>empty();
                    })
                    .onErrorResume(e -> {
                        log.error("❌ Failed to send command response for bot {}. Error: {}", botName, e.getMessage());
                        return Mono.empty();
                    });
        }

        // 理论上由于 support 方法的过滤，这里是不会执行的。
        log.warn("⚠️ MessageHandler reached final empty return unexpectedly for message: {}", text);
        return Mono.empty();
    }
}