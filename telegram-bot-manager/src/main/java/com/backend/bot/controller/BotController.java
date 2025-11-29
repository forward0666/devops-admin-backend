package com.backend.bot.controller;

import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.service.BotClientService;
import com.backend.bot.service.BotCoreService;
import com.backend.bot.dto.TelegramMarkup;
import com.backend.bot.dto.TelegramMarkup.InlineKeyboardMarkup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Duration;

@RestController
@RequiredArgsConstructor
@Slf4j
// ❌ 移除 @RequestMapping("/webhook")，以匹配 Telegram Webhook 实际发送的路径 /callback/{botName}
public class BotController {

    private final BotCoreService botCoreService;
    private final BotClientService botClientService;

    @PostMapping("/callback/{botName}") // 路径现为 /callback/{botName}，与日志中的路径一致
    @ResponseStatus(HttpStatus.OK)
    public Mono<Void> onUpdateReceived(
            @PathVariable String botName,
            @RequestBody BotUpdateDto botUpdate) {

        log.info("✅ START processing webhook for bot: {}", botName);

        return Mono.just(botUpdate)
                .doOnNext(update -> log.info("✅ STAGE 1: Webhook body received, looking up BotEntity."))
                .flatMap(update ->
                        // ❗ 重点修复：对潜在慢速的 findByBotName (R2DBC/Redis) 添加 1 秒硬性超时。
                        botCoreService.findByBotName(botName)
                                .timeout(Duration.ofSeconds(1), Mono.empty()) // 如果 1 秒内未找到，则快速返回 Mono.empty()
                                .onErrorResume(java.util.concurrent.TimeoutException.class, e -> {
                                    log.warn("⚠️ BotEntity lookup timed out (1s) during webhook processing for bot: {}", botName);
                                    return Mono.empty();
                                })
                )
                .doOnNext(entity -> log.info("STAGE 2: BotEntity found, executing business logic."))
                .flatMap(botEntity -> {
                    // 校验逻辑
                    if (botEntity.getStatus() == null || botEntity.getStatus() != 1) {
                        log.warn("⚠️ Webhook received update for inactive or unknown bot: {}", botName);
                        return Mono.empty();
                    }

                    String token = botEntity.getBotToken();
                    String botUsername = botEntity.getBotUsername();
                    String botType = botEntity.getBotType(); // 获取 botType 字符串
                    String logIdentifier = String.format("[%s/%s]", botName, botUsername);

                    // --- 1. 处理 Callback Query (按钮点击) ---
                    if (botUpdate.callbackQuery() != null) {
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
                        botClientService.sendMessage(token, chatId, responseText, null)
                                .subscribe(
                                        null,
                                        e -> log.error("❌ Failed to send response to callback query for bot {}. Error: {}", logIdentifier, e.getMessage())
                                );

                        // 确保 Webhook 链立即返回 Mono<Void>
                        return Mono.empty();
                    }


                    // --- 2. 处理 Message Update (文本消息) ---
                    if (botUpdate.message() != null && botUpdate.message().text() != null) {
                        String text = botUpdate.message().text();
                        Long chatId = botUpdate.message().chat().id();
                        String type = botUpdate.message().chat().type();


                        // --- 动态键盘逻辑：根据 /start 命令或特定关键词触发 ---
                        if (text.startsWith("/start") || ("private".equals(type) && text.contains("你好"))) {

                            log.info("✅ {} Received menu trigger message in {}: {} ", logIdentifier, type, text);

                            InlineKeyboardMarkup replyMarkup = TelegramMarkup.createDynamicKeyboard(botType);
                            String responseText = "欢迎使用！请从下方按钮中选择您需要的服务：";

                            if (replyMarkup == null) {
                                responseText = String.format("欢迎！机器人类型 [%s] 无法识别，请联系管理员。", botType);
                            }

                            log.info("✅ {} STAGE 3: Preparing to send response message with keyboard (Type: {}).", logIdentifier, botType);

                            // 异步触发，不等待结果，传入生成的键盘对象
                            botClientService.sendMessage(token, chatId, responseText, replyMarkup)
                                    .subscribe(
                                            // 添加 onError 消费，确保如果 sendMessage 失败，错误会被打印
                                            null, // onSuccess - not needed
                                            e -> log.error("❌ Failed to send START message for bot {}. Error: {}", logIdentifier, e.getMessage())
                                    );

                        } else if ("private".equals(type)) {
                            String responseText = "Bot Manager Received message: " + text + "\n请发送 /start 启动菜单。";
                            botClientService.sendMessage(token, chatId, responseText, null).subscribe();
                        } else if (text.startsWith("/status")) {
                            String responseText = "Bot Status: Active (Name: " + botName + ")";
                            botClientService.sendMessage(token, chatId, responseText, null).subscribe();
                        } else {
                            // 忽略其他消息
                            return Mono.empty();
                        }
                    }

                    // 确保主 Webhook 链立即返回 Mono<Void>
                    return Mono.empty();
                })
                // 移除外层 5 秒超时，专注于内层查找的快速失败
                .doFinally(signalType -> {
                    log.info("✅ END processing webhook for bot: {} with signal: {}", botName, signalType);
                })
                .onErrorResume(e -> {
                    log.error("❌ Error processing callback for bot {}", botName, e);
                    return Mono.empty();
                })
                .then(); // 确保返回 Mono<Void>
    }
}