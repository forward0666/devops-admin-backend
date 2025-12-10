package com.backend.bot.handler;

import com.backend.bot.constants.CallbackConstants;
import com.backend.bot.constants.TelegramConstants;
import com.backend.bot.context.HandlerContext;
import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.dto.InlineKeyboardMarkupDto;
import com.backend.bot.entity.BotConfigEntity;
import com.backend.bot.service.BotClientService;
import com.backend.bot.service.InteractiveMessageService;
import com.backend.bot.template.MenuType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView; // 引入 ContextView

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
    private final InteractiveMessageService interactiveMessageService;

    // 使用中央常量定义，确保配置统一
    private static final String MENU_PROMPT_TEXT = TelegramConstants.MENU_TIMEOUT_TEMPLATE;

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

        // 1. 🌟 关键修复：使用 Mono.deferContextual 捕获 ContextView
        return Mono.deferContextual(contextView -> {
            // 2. 编辑当前消息，更新键盘
            return botClientService.editMessageText(token, chatId, messageId, menuText, newMarkup)
                    .doOnSuccess(response -> {
                        // 3. 🌟 调用 InteractiveMessageService 封装的逻辑，并传入 ContextView
                        interactiveMessageService.scheduleMessageDeletion(
                                token,
                                userId,
                                chatId,
                                messageId,
                                delaySeconds,
                                logIdentifier,
                                contextView // <-- 传入 ContextView 以保证 traceId 传播
                        ).subscribe();
                    })
                    .onErrorResume(e -> {
                        log.error("❌ {} Failed to edit message (ID: {}) for navigation. Sending new /start prompt.", logIdentifier, messageId, e);
                        // 如果编辑失败（消息太旧），提示用户重新开始
                        return botClientService.sendMessage(token, chatId, "菜单操作失败或消息过时，请重新 /start。", null);
                    })
                    .then();
        });
    }

    /**
     * 根据回调数据判断目标菜单的级别，返回对应的销毁延迟时间。
     * 使用中央常量确保配置一致性
     */
    private int getDeletionDelay(String callbackData) {
        if (callbackData.equals(CallbackConstants.MAIN_MENU_BACK) || callbackData.startsWith(CallbackConstants.MAIN_MENU_CALLBACK)) {
            return TelegramConstants.DEFAULT_DELETE_DELAY_SECONDS;
        }
        return TelegramConstants.SECONDARY_MENU_DELETE_DELAY_SECONDS;
    }
}