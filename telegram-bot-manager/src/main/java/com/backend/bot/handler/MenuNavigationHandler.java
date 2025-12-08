package com.backend.bot.handler;

import com.backend.bot.constants.CallbackConstants;
import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.dto.InlineKeyboardMarkupDto;
import com.backend.bot.entity.BotConfigEntity;
import com.backend.bot.service.BotClientService;
import com.backend.bot.service.UserSessionService;
import com.backend.bot.template.MenuType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

/**
 * 专门处理菜单跳转（下钻和返回）逻辑的处理器。
 * 职责：编辑当前消息，更新键盘，并设置/重置自动销毁计时器。
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(10) // 优先级较高，确保菜单跳转逻辑优先于最终操作
public class MenuNavigationHandler implements CallbackActionHandler {

    private final BotClientService botClientService;
    private final UserSessionService userSessionService;

    // 自动销毁常量
    private static final int SECONDARY_MENU_DELETE_DELAY_SECONDS = 10;
    private static final int PRIMARY_MENU_DELETE_DELAY_SECONDS = 5;
    private static final String MENU_PROMPT_TEXT = "请在 %d 秒内完成操作：";

    @Override
    public boolean supports(String callbackData) {
        // 如果 MenuType 能生成键盘，则认为是菜单导航或返回操作
        // 注意：MenuType 必须是无副作用的纯函数
        return MenuType.createDynamicKeyboard(callbackData) != null;
    }

    @Override
    public int getOrder() {
        return 10;
    }

    @Override
    public Mono<Void> handle(BotConfigEntity botEntity, BotUpdateDto botUpdate) {
        String token = botEntity.getBotToken();
        String botName = botEntity.getBotName();
        String logIdentifier = String.format("[%s]", botName);

        String callbackData = botUpdate.callbackQuery().data();
        Long chatId = botUpdate.callbackQuery().message().chat().id();
        Long userId = botUpdate.callbackQuery().from().id();
        Long messageId = botUpdate.callbackQuery().message().messageId();

        // 重新获取键盘（supports() 中已确认不为空）
        InlineKeyboardMarkupDto newMarkup = MenuType.createDynamicKeyboard(callbackData);

        // 确定计时器时间
        int delaySeconds = getDeletionDelay(callbackData);
        String menuText = String.format(MENU_PROMPT_TEXT, delaySeconds);

        // 1. 编辑当前消息，更新键盘
        return botClientService.editMessageText(token, chatId, messageId, menuText, newMarkup)
                .doOnSuccess(response -> {
                    log.info("⏳ {} Edited menu (Message ID: {}). Scheduling auto-deletion in {}s.", logIdentifier, messageId, delaySeconds);

                    // 2. 安排自动删除任务 (针对已编辑的同一消息 ID)
                    Disposable deletionTask = Mono.delay(Duration.ofSeconds(delaySeconds))
                            .flatMap(aLong -> {
                                log.warn("⏰ {} Auto-deleting menu message {} after {}s timeout.", logIdentifier, messageId, delaySeconds);
                                // 尝试删除消息，然后清理会话中的 Disposable 引用
                                return botClientService.deleteMessage(token, chatId, messageId)
                                        .then(userSessionService.cancelPendingDeletion(userId));
                            })
                            .subscribeOn(Schedulers.parallel())
                            .subscribe();

                    // 3. 存储任务引用
                    userSessionService.storePendingDeletion(userId, deletionTask).subscribe();
                })
                .onErrorResume(e -> {
                    log.error("❌ {} Failed to edit message (ID: {}) for navigation. Sending new /start prompt.", logIdentifier, messageId, e);
                    // 如果编辑失败（消息太旧），提示用户重新开始
                    return botClientService.sendMessage(token, chatId, "菜单操作失败或消息过旧，请重新 /start。", null);
                })
                .then();
    }

    /**
     * 根据回调数据判断目标菜单的级别，返回对应的销毁延迟时间。
     */
    private int getDeletionDelay(String callbackData) {
        // 使用外部常量
        if (callbackData.equals(CallbackConstants.MAIN_MENU_BACK) || callbackData.startsWith(CallbackConstants.MAIN_MENU_CALLBACK)) {
            return PRIMARY_MENU_DELETE_DELAY_SECONDS;
        }
        return SECONDARY_MENU_DELETE_DELAY_SECONDS;
    }
}