package com.backend.bot.handler;

import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.dto.InlineKeyboardMarkupDto;
import com.backend.bot.entity.BotConfigEntity;
import com.backend.bot.service.BotClientService;
import com.backend.bot.template.MenuType;
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

        // 🚀 修复点 1：使用 .getDbValue() 获取枚举对应的字符串值
        String botTypeDbValue = botEntity.getBotType().getDbValue();
        String logIdentifier = String.format("[%s]", botName);

        String text = botUpdate.message().text();
        Long chatId = botUpdate.message().chat().id();
        String type = botUpdate.message().chat().type();

        // 1. --- 仅响应 /start 命令 ---
        if (text != null && text.startsWith("/start")) {
            log.info("✅ {} Received /start command in {} chat. Chat ID: {}", logIdentifier, type, chatId);

            // 动态生成内联键盘
            // 🚀 修复点 2：将 .getDbValue() 传递给需要字符串参数的方法
            InlineKeyboardMarkupDto replyMarkup = MenuType.createDynamicKeyboard(botTypeDbValue);
//            String fullWidthSpace = "\u3000";
//            U+0020 (标准空格): 容易被压缩。
//            U+3000 (全角空格): 宽度好，但在 PC 端表现不稳定。
//            U+00A0 (NBSP): 宽度窄但保证不被压缩。是跨客户端对齐的最佳折衷方案。
//            String nbs = "\u00A0";
//            String responseText = "✨✨✨ 选择服务👇👇";
//            String standardSpace = " ";
//            String padding = standardSpace.repeat(8); // 示例：重复 8 次标准空格
//
//            String responseText = String.format(
//                    "✨✨ 选择服务 👇👇",
//                    padding
//            );
            String responseText = "✨✨✨ 选择服务: 👇👇";
            if (replyMarkup == null) {
                // 🚀 修复点 3：日志中使用 .getDbValue()
                responseText = String.format("欢迎！机器人类型 [%s] 无法识别，请联系管理员。", botTypeDbValue);
            }

            // 🚀 修复点 4：日志中使用 .getDbValue()
            log.info("✅ {} STAGE 3: Preparing to send response message with keyboard (Type: {}).", logIdentifier, botTypeDbValue);

            return botClientService.sendMessage(token, chatId, responseText, replyMarkup)
                    .onErrorResume(e -> {
                        log.error("❌ Failed to send START message for bot {}. Error: {}", logIdentifier, e.getMessage());
                        return Mono.empty();
                    })
                    .then();
        }

//        // 2. --- /status 命令处理 ---
//        else if ("private".equals(type) && text.startsWith("/status")) {
//            String responseText = "Bot Status: Active (Chat ID: " + chatId + ")";
//            return botClientService.sendMessage(token, chatId, responseText, null)
//                    .onErrorResume(e -> Mono.empty())
//                    .then();
//        }

        // 3. --- 忽略其他所有消息 ---
        log.debug("Skipping message update (Type: {}): {}", type, text);
        return Mono.empty();
    }
}