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
import reactor.util.context.ContextView;

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

    // 🌟 恢复到静态菜单文本模板，与 TelegramConstants 保持一致
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

        // 🌟 恢复：使用静态模板和延迟时间格式化文本
        String menuText = String.format(MENU_PROMPT_TEXT, delaySeconds);

        // 1. 🌟 关键修复：使用 Mono.deferContextual 捕获 ContextView
        return Mono.deferContextual(contextView -> {
            final String traceLogPrefix = com.backend.bot.util.LogUtils.prepareMdcAndGetPrefix(contextView);

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
                    .onErrorResume(e -> { // 外部异常 e
                        String errorMessage = e.getMessage();

                        // 保持关键修复：将 400 Bad Request 记录为 INFO
                        if (errorMessage != null && errorMessage.contains("400 Bad Request")) {
                            // 这是一个非致命错误，通常是消息未修改（最常见原因，例如双击）。
                            log.info("{}💬 Could not edit message (ID: {}). Reason: {}. Likely no modification occurred.",
                                    traceLogPrefix, messageId, errorMessage);

                            // 确保返回空流，阻止错误继续传播，并避免发送新消息。
                            return Mono.empty();
                        }

                        // 对于其他致命错误（如网络问题，鉴权失败等），继续记录 WARN
                        log.warn("{}⚠️ Could not edit message (ID: {}). Reason: {}. Not sending new message to avoid duplicate menus.",
                                traceLogPrefix, messageId, errorMessage);
                        return Mono.empty();
                    })
                    .then();
        });
    }

    /**
     * 统一返回菜单的销毁延迟时间。
     * 一级菜单和二级菜单都使用相同的销毁时间
     */
    private int getDeletionDelay(String callbackData) {
        // 使用统一的菜单删除延迟常量
        return TelegramConstants.MENU_DELETE_DELAY_SECONDS;
    }
}