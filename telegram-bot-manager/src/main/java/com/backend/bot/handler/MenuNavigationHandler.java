package com.backend.bot.handler;

import com.backend.bot.constants.CallbackConstants;
import com.backend.bot.context.HandlerContext;
import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.dto.InlineKeyboardMarkupDto;
import com.backend.bot.entity.BotConfigEntity;
import com.backend.bot.service.BotClientService;
import com.backend.bot.service.InteractiveMessageService; // 🌟 引入新服务
import com.backend.bot.template.MenuType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

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
    private final InteractiveMessageService interactiveMessageService; // 🌟 注入新服务

    // 自动销毁常量
    private static final int SECONDARY_MENU_DELETE_DELAY_SECONDS = 10;
    private static final int PRIMARY_MENU_DELETE_DELAY_SECONDS = 5;
    private static final String MENU_PROMPT_TEXT = "请在 %d 秒内完成操作：";

    @Override
    public boolean supports(String callbackData) {
        return MenuType.createDynamicKeyboard(callbackData) != null;
    }

    @Override
    public int getOrder() {
        return 10;
    }

    @Override
    public Mono<Void> handle(BotConfigEntity botEntity, BotUpdateDto botUpdate) {
        // 🌟 使用 HandlerContext 简化数据提取
        HandlerContext context = new HandlerContext(botEntity, botUpdate);

        String token = context.token();
        String logIdentifier = context.logIdentifier();
        Long chatId = context.chatId();
        Long userId = context.userId();
        Long messageId = context.messageId();

        String callbackData = botUpdate.callbackQuery().data();

        InlineKeyboardMarkupDto newMarkup = MenuType.createDynamicKeyboard(callbackData);

        // 确定计时器时间
        int delaySeconds = getDeletionDelay(callbackData);
        String menuText = String.format(MENU_PROMPT_TEXT, delaySeconds);

        // 1. 编辑当前消息，更新键盘
        return botClientService.editMessageText(token, chatId, messageId, menuText, newMarkup)
                .doOnSuccess(response -> {
                    // 2. 🌟 调用 InteractiveMessageService 封装的逻辑来安排自动删除任务
                    interactiveMessageService.scheduleMessageDeletion(
                            token,
                            userId,
                            chatId,
                            messageId,
                            delaySeconds,
                            logIdentifier
                    ).subscribe();
                })
                .onErrorResume(e -> {
                    log.error("❌ {} Failed to edit message (ID: {}) for navigation. Sending new /start prompt.", logIdentifier, messageId, e);
                    // 如果编辑失败（消息太旧），提示用户重新开始
                    return botClientService.sendMessage(token, chatId, "菜单操作失败或消息过时，请重新 /start。", null);
                })
                .then();
    }

    /**
     * 根据回调数据判断目标菜单的级别，返回对应的销毁延迟时间。
     */
    private int getDeletionDelay(String callbackData) {
        if (callbackData.equals(CallbackConstants.MAIN_MENU_BACK) || callbackData.startsWith(CallbackConstants.MAIN_MENU_CALLBACK)) {
            return PRIMARY_MENU_DELETE_DELAY_SECONDS;
        }
        return SECONDARY_MENU_DELETE_DELAY_SECONDS;
    }
}