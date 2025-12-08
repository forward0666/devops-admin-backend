package com.backend.bot.handler;

import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.dto.InlineKeyboardMarkupDto;
import com.backend.bot.entity.BotConfigEntity;
import com.backend.bot.service.BotClientService;
import com.backend.bot.template.MenuType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order; // 引入 Order
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(10) // 🌟 优先级设置：设置为 10，比回调低，比普通文本高
public class MessageHandler implements UpdateHandler {

    private final BotClientService botClientService;

    @Override
    public boolean support(BotUpdateDto update) {
        // 🌟 关键修复：MessageHandler 应该只处理命令（以 '/' 开头的文本）
        if (update.message() != null && update.message().text() != null) {
            String text = update.message().text().trim();
            // 确保是命令，而不是普通文本
            return !text.isEmpty() && text.startsWith("/");
        }
        return false;
    }

    @Override
    public Mono<Void> handle(BotConfigEntity botEntity, BotUpdateDto botUpdate) {
        String token = botEntity.getBotToken();
        String botName = botEntity.getBotName();

        // ... [省略 handle 方法中未改动的逻辑] ...

        String botTypeDbValue = botEntity.getBotType().getDbValue();

        String text = botUpdate.message().text();
        Long chatId = botUpdate.message().chat().id();
        String type = botUpdate.message().chat().type();

        // 1. --- 仅响应 /start 命令 ---
        if (text != null && text.startsWith("/start")) {
            // 🌟 只需要依赖 Logback/Log4j2 自动打印 MDC 中的 traceId 即可
            log.info("✅ Received /start command for bot: {} in {} chat. Chat ID: {}", botName, type, chatId);

            // 动态生成内联键盘
            InlineKeyboardMarkupDto replyMarkup = MenuType.createDynamicKeyboard(botTypeDbValue);

            String responseText = "✨✨✨ 选择服务: 👇👇";
            if (replyMarkup == null) {
                responseText = String.format("欢迎！机器人类型 [%s] 无法识别，请联系管理员。", botTypeDbValue);
            }

            // 🌟 只需要依赖 Logback/Log4j2 自动打印 MDC 中的 traceId 即可
            log.info("✅ STAGE 3: Preparing to send response message with keyboard (Bot: {}, Type: {}).", botName, botTypeDbValue);

            return botClientService.sendMessage(token, chatId, responseText, replyMarkup)
                    .onErrorResume(e -> {
                        // 🌟 只需要依赖 Logback/Log4j2 自动打印 MDC 中的 traceId 即可
                        log.error("❌ Failed to send START message for bot {}. Error: {}", botName, e.getMessage());
                        return Mono.empty();
                    })
                    .then();
        }

        // 3. --- 忽略其他所有消息 ---
        log.debug("Skipping message update for bot {} (Type: {}): {}", botName, type, text);
        return Mono.empty();
    }
}