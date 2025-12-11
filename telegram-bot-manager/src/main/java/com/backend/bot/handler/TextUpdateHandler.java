package com.backend.bot.handler;

import com.backend.bot.constants.TelegramConstants;
import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.dto.UserDto; // 引入 User DTO
import com.backend.bot.entity.BotConfigEntity;
import com.backend.bot.entity.UserSessionEntity;
import com.backend.bot.service.BotClientService;
import com.backend.bot.service.UserSessionService;
import com.backend.bot.service.WhitelistService;
import com.backend.bot.util.BotUserUtils; // 引入新增的工具类
import com.backend.bot.util.LogUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 导入 CallbackQueryHandler 中的状态常量
import static com.backend.bot.constants.CallbackConstants.*;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(20) // 优先级最低，处理普通文本/会话
public class TextUpdateHandler implements UpdateHandler {

    private final BotClientService botClientService;
    private final UserSessionService userSessionService;
    private final WhitelistService whitelistService;

    // 定义用于解析输入的正则表达式
    private static final String IP_USER_PATTERN_REGEX = "^(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})\\+(\\w+)$";
    private static final Pattern IP_USER_PATTERN = Pattern.compile(IP_USER_PATTERN_REGEX);
    private static final String PARSE_ERROR_TEXT = "输入格式错误！请确保格式为：IP+目标用户 (e.g. 1.1.1.1+forward)。";

    @Override
    public boolean support(BotUpdateDto update) {
        boolean isMessagePresent = update.message() != null;
        boolean isCallbackPresent = update.callbackQuery() != null;

        // 尝试获取原始文本。如果为 null，则设为 ""。
        String rawText = isMessagePresent && update.message().text() != null ? update.message().text() : "";
        // 对文本进行修剪 (trim)
        String text = rawText.trim();

        String logText = text.isEmpty() ? "N/A" : text;
        int textLength = text.length();

        log.debug("🔍 TextUpdateHandler Check (DEBUG): Msg={}, Callback={}, TrimmedText='{}' (Length: {})",
                isMessagePresent, isCallbackPresent, logText.length() > 50 ? logText.substring(0, 50) + "..." : logText, textLength);


        if (!isMessagePresent) {
            log.debug("⚠️ TextUpdateHandler rejected (DEBUG): No message object in update.");
            return false;
        }

        if (text.isEmpty()) {
            log.debug("⚠️ TextUpdateHandler rejected (DEBUG): Message object present, but text is empty after trimming (e.g., photo, sticker, or just spaces).");
            return false;
        }

        // 确保不是以 '/' 开头的命令
        if (text.startsWith("/")) {
            log.debug("⚠️ TextUpdateHandler rejected (DEBUG): Starts with '/' (Command).");
            return false;
        }

        log.debug("✅ TextUpdateHandler accepted (DEBUG): Message is non-command text.");
        return true;
    }

    @Override
    public Mono<Void> handle(BotConfigEntity botEntity, BotUpdateDto botUpdate) {
        return Mono.deferContextual(contextView -> {
            // 提取 Trace ID 并设置 MDC
            final String traceLogPrefix = LogUtils.prepareMdcAndGetPrefix(contextView);

            // 提取 Bot 配置信息
            final String token = botEntity.getBotToken();
            final String botName = botEntity.getBotName();
            final String logIdentifier = String.format("[%s]", botName);

            log.debug("{}✅ {} TextUpdateHandler successfully entered handle method.", traceLogPrefix, logIdentifier);
            log.info("{}📥 {} Full Update DTO received: {}", traceLogPrefix, logIdentifier, botUpdate);

            // 🌟 优化：在方法开始处统一提取并声明为 final，使代码更具可读性和响应式安全
            final String userText = botUpdate.message().text().trim();
            final Long chatId = botUpdate.message().chat().id();

            // --- 优化点：使用 BotUserUtils 提取 User 和 OperatorName ---
            final UserDto user = BotUserUtils.extractUser(botUpdate)
                    .orElseThrow(() -> new IllegalStateException("User object is missing in a supported text update."));
            final Long userId = user.id();
            final String finalOperatorName = BotUserUtils.getOperatorName(user, userId);
            // --------------------------------------------------------

            // 1. 获取用户当前会话状态
            return userSessionService.getUserSession(userId)
                    .flatMap(session -> {
                        // 统一使用 session.getState()
                        String state = session.getState();
                        log.info("{}📝 {} User {} (Op: {}) current state is: {}", traceLogPrefix, logIdentifier, userId, finalOperatorName, state);

                        // 2. 根据状态进行分发处理
                        return switch (state) {
                            case STATE_AWAITING_FRONTEND_WEB_IP, STATE_AWAITING_FRONTEND_ADMIN_IP ->
                                handleAwaitingIpInput(token, chatId, userId, userText, session, finalOperatorName, traceLogPrefix);
                            case TelegramConstants.SESSION_STATE_PROCESSING_START ->
                                // 用户正在处理/start命令，忽略文本输入
                                handleProcessingStart(token, chatId, logIdentifier, traceLogPrefix);
                            default ->
                                // 默认行为：如果不是任何等待状态，可能是普通聊天或 /start 命令
                                handleDefaultText(token, chatId, userText, logIdentifier, traceLogPrefix);
                        };
                    })
                    // 用户没有会话
                    .switchIfEmpty(handleDefaultText(token, chatId, userText, logIdentifier, traceLogPrefix))
                    .then();
        });
    }

    /**
     * 处理等待 IP 和目标标识输入的逻辑。
     * 新增 traceLogPrefix 参数用于日志输出。
     */
    private Mono<Void> handleAwaitingIpInput(String token, Long chatId, Long userId, String userText, UserSessionEntity session, String operatorName, String traceLogPrefix) {
        String state = session.getState();
        String domainType = getDomainType(state);

        // 1. 验证输入格式
        Matcher matcher = IP_USER_PATTERN.matcher(userText);
        if (!matcher.matches()) {
            // 格式错误，发送提示并保持状态
            return botClientService.sendMessage(token, chatId, PARSE_ERROR_TEXT, null).then();
        }

        // 2. 解析 IP 和目标标识
        String ip = matcher.group(1);
        String targetIdentifier = matcher.group(2);

        // 打印 Trace ID 日志
        log.info("{}✅ Parsed input for {}. IP: {}, Target ID: {}, Operator: {}", traceLogPrefix, domainType, ip, targetIdentifier, operatorName);

        // 3. 执行加白操作 (传入目标标识和操作人)
        // Mono<String> 包含最终发送给用户的消息
        Mono<String> whitelistResultMono = whitelistService.addIpToWhitelist(ip, targetIdentifier, domainType)
                .map(success -> {
                    if (success) {
                        // 最终消息使用格式化的 operatorName
                        return String.format("🎉 %s 加白成功！\n\n- 目标: %s\n- 用户: %s\n- IP: %s\n- 操作人: %s",
                                domainType, domainType, targetIdentifier, ip, operatorName);
                    } else {
                        return "❌ 加白失败！请联系管理员。";
                    }
                })
                .onErrorReturn("❌ 系统错误！加白服务异常，请联系管理员。");


        // 4. 【修复后的顺序】先等待加白操作结果，然后执行**链式**清理会话，最后发送结果。
        return whitelistResultMono
                // FIX: 使用 flatMap 接收结果文本
                .flatMap(resultText ->
                        // 1. **链式**执行取消待删除任务操作
                        userSessionService.cancelPendingDeletion(userId)
                                // 2. 然后 (then) 执行会话清除操作。clearUserSession(userId)返回一个 Mono<Void>。
                                .then(userSessionService.clearUserSession(userId))
                                // 3. 然后 (then) 链式执行发送消息操作。
                                .then(botClientService.sendMessage(token, chatId, resultText, null))
                )
                // 确保整个流程最终返回 Mono<Void>
                .then();
    }

    /**
     * 处理用户正在处理/start命令时的文本输入
     */
    private Mono<Void> handleProcessingStart(String token, Long chatId, String logIdentifier, String traceLogPrefix) {
        log.debug("{}🤫 {} User text input ignored while processing /start command", traceLogPrefix, logIdentifier);
        return Mono.empty();
    }

    /**
     * 处理普通文本输入，例如 /start 或未处于会话状态时的消息。
     * 新增 traceLogPrefix 参数用于日志输出。
     */
    private Mono<Void> handleDefaultText(String token, Long chatId, String userText, String logIdentifier, String traceLogPrefix) {
        // 移除对 /start 命令的处理，统一由 StartCommandHandler 处理
        // 这样可以确保在二级菜单时点击 /start 不会创建新菜单，而是提示用户先完成当前操作
        
        // 修复：忽略其他普通文本，不再回复任何提示。
        // 打印 Trace ID 日志
        log.debug("{}🤫 {} Ignoring non-session text: {}", traceLogPrefix, logIdentifier, userText);
        return Mono.empty();
    }

    // 辅助方法：将状态映射回域名类型
    private String getDomainType(String state) {
        return switch (state) {
            case STATE_AWAITING_FRONTEND_WEB_IP -> "前端前台域名";
            case STATE_AWAITING_FRONTEND_ADMIN_IP -> "前端后台域名";
            default -> "未知";
        };
    }

    // 原有的 getOperatorName 辅助方法已移除，移入 BotUserUtils 类中
}