package com.backend.bot.handler;

import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.dto.InlineKeyboardMarkupDto;
import com.backend.bot.entity.BotConfigEntity;
import com.backend.bot.service.BotClientService;
import com.backend.bot.service.InMemoryUserSessionService;
import com.backend.bot.template.MenuType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(1) // 优先级改为最高，确保先于 MessageHandler (10) 执行
public class StartCommandHandler implements UpdateHandler {

    private final BotClientService botClientService;
    private final InMemoryUserSessionService userSessionService;
    private final ObjectMapper objectMapper; // 用于解析 sendMessage 响应获取 messageId

    // 菜单消息文本
    private static final String WELCOME_TEXT = "✨✨✨ 选择服务: 👇👇";
    private static final int DELETE_DELAY_SECONDS = 5;

    @Override
    public boolean support(BotUpdateDto update) {
        boolean isStartCommand = update.message() != null &&
                update.message().text() != null &&
                update.message().text().trim().startsWith("/start");

        if (isStartCommand) {
            log.info("✅ StartCommandHandler accepted: /start command.");
        }
        return isStartCommand;
    }

    @Override
    public Mono<Void> handle(BotConfigEntity botEntity, BotUpdateDto botUpdate) {
        String token = botEntity.getBotToken();
        String botName = botEntity.getBotName();
        Long chatId = botUpdate.message().chat().id();
        Long userId = botUpdate.message().from().id();
        String logIdentifier = String.format("[%s]", botName);

        log.info("🚀 {} Handling /start command from userId: {}", logIdentifier, userId);

        // 1. 生成主菜单键盘
        InlineKeyboardMarkupDto mainMenuMarkup = MenuType.createMainMenu();

        // 2. 发送主菜单消息，并获取 messageId
        // 假设 botClientService.sendMenuMessageWithResponse 成功返回 JSON 字符串
        Mono<String> sendMenuResponseMono = botClientService.sendMenuMessageWithResponse(token, chatId, WELCOME_TEXT, mainMenuMarkup);

        return sendMenuResponseMono
                .doOnNext(responseJson -> {
                    // 🌟 诊断日志：打印完整的响应JSON，以便检查结构
                    log.debug("🔍 {} Received Telegram sendMessage response JSON: {}", logIdentifier, responseJson);

                    try {
                        // 尝试解析JSON
                        JsonNode root = objectMapper.readTree(responseJson);
                        JsonNode resultNode = root.path("result");

                        // 检查 'ok' 字段是否为 true (Telegram API标准)
                        boolean isOk = root.path("ok").asBoolean();

                        if (!isOk || resultNode.isMissingNode()) {
                            log.error("❌ {} Telegram API returned failure (ok=false) or missing 'result' node in response: {}", logIdentifier, responseJson);
                            return; // 失败则退出
                        }

                        Long messageId = resultNode.path("message_id").asLong();

                        if (messageId != 0) {
                            // 🌟 成功启动计时器的日志
                            log.info("⏳ {} Menu message sent with ID: {}. Scheduling auto-deletion in {}s.", logIdentifier, messageId, DELETE_DELAY_SECONDS);

                            // 3. 安排自动删除任务
                            Disposable deletionTask = Mono.delay(Duration.ofSeconds(DELETE_DELAY_SECONDS))
                                    .flatMap(aLong -> {
                                        log.warn("⏰ {} Auto-deleting menu message {} after {}s timeout.", logIdentifier, messageId, DELETE_DELAY_SECONDS);
                                        // 3.1 执行删除操作 (需要 BotClientService 中有 deleteMessage 方法)
                                        return botClientService.deleteMessage(token, chatId, messageId)
                                                // 3.2 清除存储的任务引用
                                                .then(userSessionService.cancelPendingDeletion(userId));
                                    })
                                    .subscribeOn(Schedulers.parallel()) // 在单独的线程上执行延迟
                                    .subscribe();

                            // 4. 存储任务引用，以便用户点击按钮时可以取消
                            userSessionService.storePendingDeletion(userId, deletionTask).subscribe();
                        } else {
                            log.warn("⚠️ {} Message ID extraction failed (messageId=0) or message was not sent correctly.", logIdentifier);
                        }
                    } catch (Exception e) {
                        // 🌟 关键错误日志：如果 JSON 解析失败，必定会打印此行
                        log.error("❌ {} Failed to parse sendMessage response or schedule deletion. JSON: {}", logIdentifier, responseJson, e);
                    }
                })
                .onErrorResume(e -> {
                    // 2.3 发送消息本身失败 (例如网络错误)
                    log.error("❌ {} Failed to send initial menu message.", logIdentifier, e);
                    return Mono.empty(); // 忽略发送失败
                })
                .then();
    }
}