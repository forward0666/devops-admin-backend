package com.backend.bot.service;

import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.dto.InlineKeyboardMarkupDto;
import com.backend.bot.template.TelegramMarkup;
import com.backend.bot.entity.BotEntity; // 假设 BotEntity 位于这个包
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class BotUpdateHandlerService {

    private final BotClientService botClientService;

    /**
     * 根据 Bot 实体和接收到的 Update 对象处理所有业务逻辑。
     * @param botEntity 数据库中查找到的 Bot 实体
     * @param botUpdate Telegram Webhook Update DTO
     * @return Mono<Void> 表示处理完成
     */
    public Mono<Void> handleUpdate(BotEntity botEntity, BotUpdateDto botUpdate) {

        String token = botEntity.getBotToken();
        String botName = botEntity.getBotName();
        String botUsername = botEntity.getBotUsername();
        String botType = botEntity.getBotType();
        String logIdentifier = String.format("[%s/%s]", botName, botUsername);

        // --- 1. 处理 Callback Query (按钮点击) ---
        if (botUpdate.callbackQuery() != null) {
            return handleCallbackQuery(token, botUpdate, logIdentifier, botType);
        }

        // --- 2. 处理 Message Update (文本消息) ---
        if (botUpdate.message() != null && botUpdate.message().text() != null) {
            return handleMessageUpdate(token, botUpdate, logIdentifier, botType);
        }

        // 3. 忽略其他类型的 Update
        return Mono.empty();
    }

    private Mono<Void> handleCallbackQuery(String token, BotUpdateDto botUpdate, String logIdentifier, String botType) {
        String callbackData = botUpdate.callbackQuery().data();
        Long chatId = botUpdate.callbackQuery().message().chat().id();
        String callbackQueryId = botUpdate.callbackQuery().id();

        log.info("⚙️ {} Received callback query: {}", logIdentifier, callbackData);

        // 1.1 立即响应 callback_query 以停止按钮上的加载动画
        botClientService.answerCallbackQuery(token, callbackQueryId, "已接收请求: " + callbackData)
                .subscribe(
                        null,
                        e -> log.error("❌ Failed to answer callback query for bot {}. Error: {}", logIdentifier, e.getMessage())
                );

        // 1.2 根据 callbackData 执行业务逻辑 (示例：回复一个消息)
        String responseText = "您点击了: " + callbackData + "。 Bot类型: " + botType;

        // 确保 Webhook 链立即返回 Mono<Void>，同时异步发送消息
        return botClientService.sendMessage(token, chatId, responseText, null)
                .onErrorResume(e -> {
                    log.error("❌ Failed to send response to callback query for bot {}. Error: {}", logIdentifier, e.getMessage());
                    return Mono.empty();
                })
                .then(Mono.empty());
    }

//    private Mono<Void> handleMessageUpdate(String token, BotUpdateDto botUpdate, String logIdentifier, String botType) {
//        String text = botUpdate.message().text();
//        Long chatId = botUpdate.message().chat().id();
//        String type = botUpdate.message().chat().type();
//
//        // --- 动态键盘逻辑：根据 /start 命令或特定关键词触发 ---
//        if (text.startsWith("/start") || ("private".equals(type) && text.contains("你好"))) {
//
//            log.info("✅ {} Received menu trigger message in {}: {} ", logIdentifier, type, text);
//
//            InlineKeyboardMarkup replyMarkup = TelegramMarkup.createDynamicKeyboard(botType);
//            String responseText = "欢迎使用！请从下方按钮中选择您需要的服务：";
//
//            if (replyMarkup == null) {
//                responseText = String.format("欢迎！机器人类型 [%s] 无法识别，请联系管理员。", botType);
//            }
//
//            log.info("✅ {} STAGE 3: Preparing to send response message with keyboard (Type: {}).", logIdentifier, botType);
//
//            // 异步触发，不等待结果，传入生成的键盘对象
//            return botClientService.sendMessage(token, chatId, responseText, replyMarkup)
//                    .onErrorResume(e -> {
//                        log.error("❌ Failed to send START message for bot {}. Error: {}", logIdentifier, e.getMessage());
//                        return Mono.empty();
//                    })
//                    .then(Mono.empty()); // 确保返回 Mono<Void>
//        }
//
//        // --- 其他文本消息处理 ---
//        else if ("private".equals(type)) {
//            String responseText = "Bot Manager Received message: " + text + "\n请发送 /start 启动菜单。";
//            return botClientService.sendMessage(token, chatId, responseText, null)
//                    .onErrorResume(e -> Mono.empty())
//                    .then(Mono.empty());
//        } else if (text.startsWith("/status")) {
//            String responseText = "Bot Status: Active (Name: " + botUpdate.callbackQuery().message().chat().id() + ")"; // 注意：这里botName可能更合理
//            return botClientService.sendMessage(token, chatId, responseText, null)
//                    .onErrorResume(e -> Mono.empty())
//                    .then(Mono.empty());
//        }
//
//        // 忽略其他消息
//        return Mono.empty();
//    }

    private Mono<Void> handleMessageUpdate(String token, BotUpdateDto botUpdate, String logIdentifier, String botType) {
        // 使用 Record 访问器 message() 来获取 MessageDto
        String text = botUpdate.message().text();
        Long chatId = botUpdate.message().chat().id();
        String type = botUpdate.message().chat().type(); // private, group, supergroup, channel

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

            // 异步发送带键盘的消息
            return botClientService.sendMessage(token, chatId, responseText, replyMarkup)
                    .onErrorResume(e -> {
                        log.error("❌ Failed to send START message for bot {}. Error: {}", logIdentifier, e.getMessage());
                        return Mono.empty();
                    })
                    .then(); // 确保返回 Mono<Void>
        }

        // 2. --- 修正后的 /status 命令处理 (可选，但建议修正逻辑) ---
        // 如果您需要处理 /status，且只在私聊中响应，可以这样写：
//        /*
        else if ("private".equals(type) && text.startsWith("/status")) {
            // 修正：使用 botUpdate.message().chat().id() 或 chatId
//            String responseText = "Bot Status: Active (Chat ID: " + chatId + " | Type: " + botType + ")";
            String responseText = "Bot Status: Active (Chat ID: " + chatId + ")";
            return botClientService.sendMessage(token, chatId, responseText, null)
                    .onErrorResume(e -> Mono.empty())
                    .then();
        }
//        */

        // 3. --- 忽略其他所有消息 ---
        // 根据您的要求，除了 /start 以外的所有消息都将忽略（返回 Mono.empty()）
        log.debug("Skipping message update (Type: {}): {}", type, text);
        return Mono.empty();
    }
}