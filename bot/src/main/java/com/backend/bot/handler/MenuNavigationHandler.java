package com.backend.bot.handler;

import com.backend.bot.constants.TelegramConstants;
import com.backend.bot.context.HandlerContext;
import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.dto.InlineKeyboardMarkupDto;
import com.backend.bot.entity.BotConfigEntity;
import com.backend.bot.service.BotClientService;
import com.backend.bot.service.BotMenuService;
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
    private final BotMenuService botMenuService;

    // 修复: 将秒数占位符从 %d 更改为 %s，以避免 java.util.IllegalFormatConversionException
    // 优化: 缩短菜单提示文本，移除“当前菜单:”前缀、菜单名称及其周围的方括号【】，只保留计时器信息
    // 注意: 菜单名称 (%s) 现已移除，只保留计时器 (%s)
    private static final String MENU_PROMPT_TEXT_TEMPLATE = "⏱️ 窗口剩余 %s 秒，请及时选择操作或发送 /start 重新打开菜单。";

    @Override
    public boolean supports(String callbackData) {
        if (callbackData == null) return false;
        // 排除动态 handler 处理的回调
        if (callbackData.startsWith("callback_data_PURGECACHE_") || callbackData.startsWith("callback_data_PURGE_RULE_")) return false;
        // 支持 fallback 硬编码
        if (MenuType.createFallbackKeyboard(callbackData) != null) return true;
        // 支持数据库中的菜单（callback_data_ 前缀的都可能是菜单导航）
        if (callbackData.startsWith("callback_data_")) return true;
        return false;
    }

    @Override
    public int getOrder() {
        return 10;
    }

    @Override
    public Mono<Void> handle(BotConfigEntity botEntity, BotUpdateDto botUpdate) {
        // 使用 HandlerContext 简化数据提取
        HandlerContext context = new HandlerContext(botEntity, botUpdate);

        String token = context.token();
        String logIdentifier = context.logIdentifier();
        Long chatId = context.chatId();
        Long userId = context.userId();
        Long messageId = context.messageId();

        String callbackData = botUpdate.callbackQuery().data();

        // 1. 获取新的键盘（优先从数据库查询，fallback 到硬编码）
        final InlineKeyboardMarkupDto fallbackMarkup = MenuType.createFallbackKeyboard(callbackData);
        final String menuKey = callbackData.startsWith("callback_data_")
                ? callbackData.substring("callback_data_".length())
                : callbackData;

        // 2. 确定计时器时间 (int)
        int delaySeconds = getDeletionDelay(callbackData);

        // 3. 动态生成菜单文本（加时间戳避免TG editMessageText 400）
        String menuText = String.format(
                MENU_PROMPT_TEXT_TEMPLATE,
                String.valueOf(delaySeconds)
        ) + "\n\u200B";

        // 使用 Mono.deferContextual 捕获 ContextView
        return Mono.deferContextual(contextView -> {
            final String traceLogPrefix = com.backend.bot.util.LogUtils.prepareMdcAndGetPrefix(contextView);

            // 先从数据库查
            log.info("{}🔍 [MenuNav] Searching DB: botName={}, menuKey={}", traceLogPrefix, context.botName(), menuKey);
            return botMenuService.findKeyboardByBotTypeAndMenuKey(context.botEntity().getBotType().getDbValue(), menuKey)
                    .flatMap(newMarkup -> {
                        if (newMarkup == null || newMarkup.isEmpty()) return Mono.empty();
                        return editWithKeyboard(token, chatId, messageId, menuText, newMarkup, userId, logIdentifier, delaySeconds, contextView, traceLogPrefix);
                    })
                    // DB 查不到 → 试试 fallback 硬编码
                    .switchIfEmpty(Mono.defer(() -> {
                        if (fallbackMarkup != null && !fallbackMarkup.isEmpty()) {
                            return editWithKeyboard(token, chatId, messageId, menuText, fallbackMarkup, userId, logIdentifier, delaySeconds, contextView, traceLogPrefix);
                        }
                        // 都没有 → 不是菜单导航，返回 Mono.error 让后续 handler 处理
                        return Mono.error(new UnsupportedOperationException("Not a menu navigation callback"));
                    }))
                    // 不是菜单导航，静默跳过
                    .onErrorResume(UnsupportedOperationException.class, e -> Mono.empty());
        });
    }

    private Mono<Void> editWithKeyboard(String token, Long chatId, Long messageId, String menuText,
                                          InlineKeyboardMarkupDto newMarkup, Long userId, String logIdentifier,
                                          int delaySeconds, ContextView contextView, String traceLogPrefix) {
        return botClientService.editMessageText(token, chatId, messageId, menuText, newMarkup)
                .doOnSuccess(response -> {
                    interactiveMessageService.scheduleMessageDeletion(
                            token, userId, chatId, messageId, delaySeconds, logIdentifier, contextView
                    ).subscribe();
                })
                .onErrorResume(e -> {
                    log.warn("{}⚠️ Non-400 error during message edit (ID: {}). Reason: {}.", traceLogPrefix, messageId, e.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    private int getDeletionDelay(String callbackData) {
        // 使用统一的菜单删除延迟常量
        return TelegramConstants.MENU_DELETE_DELAY_SECONDS;
    }
}