package com.backend.bot.handler;

import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.entity.BotConfigEntity;
import com.backend.bot.entity.UserSessionEntity;
import com.backend.bot.event.BotUpdateEvent;
import com.backend.bot.service.BotClientService;
import com.backend.bot.service.UserSessionService;
import com.backend.bot.service.WhitelistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 导入 CallbackQueryHandler 中的状态常量
import static com.backend.bot.constants.CallbackConstants.*;
import static com.backend.bot.handler.CallbackQueryHandler.*;

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

        // 用于日志输出：如果修剪后文本为空，则显示 N/A，否则显示修剪后的文本
        String logText = text.isEmpty() ? "N/A" : text;
        int textLength = text.length();

        // 【关键调试 - 级别改为 INFO】确认 support 方法被调用，并打印核心结构信息
        log.info("🔍 TextUpdateHandler Check (INFO): Msg={}, Callback={}, TrimmedText='{}' (Length: {})",
                isMessagePresent, isCallbackPresent, logText.length() > 50 ? logText.substring(0, 50) + "..." : logText, textLength);


        // 【正式逻辑检查点】
        if (!isMessagePresent) {
            // 【拒绝日志 - 级别改为 INFO】
            log.info("⚠️ TextUpdateHandler rejected (INFO): No message object in update.");
            return false;
        }

        // 检查文本是否为空 (包括只包含空格的情况)
        if (text.isEmpty()) {
            // 【拒绝日志 - 级别改为 INFO】
            log.info("⚠️ TextUpdateHandler rejected (INFO): Message object present, but text is empty after trimming (e.g., photo, sticker, or just spaces).");
            return false;
        }

        // 确保不是以 '/' 开头的命令
        if (text.startsWith("/")) {
            // 【拒绝日志 - 级别改为 INFO】
            log.info("⚠️ TextUpdateHandler rejected (INFO): Starts with '/' (Command).");
            return false;
        }

        // 仅处理包含非命令文本内容的消息
        // 【成功日志 - 级别改为 INFO】
        log.info("✅ TextUpdateHandler accepted (INFO): Message is non-command text.");
        return true;
    }

    @Override
    public Mono<Void> handle(BotConfigEntity botEntity, BotUpdateDto botUpdate) {
        // 提取 Bot 配置信息
        final String token = botEntity.getBotToken();
        final String botName = botEntity.getBotName();
        final String logIdentifier = String.format("[%s]", botName);

        // 【新增调试日志】确认进入 handle 方法
        log.debug("✅ {} TextUpdateHandler successfully entered handle method.", logIdentifier);

        // 打印整个接收到的 BotUpdateDto，以便完整查看信息
        log.info("📥 {} Full Update DTO received: {}", logIdentifier, botUpdate);

        // 🌟 优化：在方法开始处统一提取并声明为 final，使代码更具可读性和响应式安全
        final String userText = botUpdate.message().text().trim();
        final Long chatId = botUpdate.message().chat().id();
        final Long userId = botUpdate.message().from().id();
        final String finalOperatorName = getOperatorName(botUpdate, userId);

        // 1. 获取用户当前会话状态
        return userSessionService.getUserSession(userId)
                .flatMap(session -> {
                    // 统一使用 session.getState()
                    String state = session.getState();
                    log.info("📝 {} User {} (Op: {}) current state is: {}", logIdentifier, userId, finalOperatorName, state);

                    // 2. 根据状态进行分发处理
                    return switch (state) {
                        case STATE_AWAITING_FRONTEND_WEB_IP, STATE_AWAITING_FRONTEND_ADMIN_IP ->
                                handleAwaitingIpInput(token, chatId, userId, userText, session, finalOperatorName);
                        default ->
                            // 默认行为：如果不是任何等待状态，可能是普通聊天或 /start 命令
                                handleDefaultText(token, chatId, userText, logIdentifier);
                    };
                })
                .switchIfEmpty(handleDefaultText(token, chatId, userText, logIdentifier)) // 用户没有会话
                .then();
    }

    /**
     * 辅助方法：格式化操作人名称 (firstName + (@username) 或 userId)
     */
    private static String getOperatorName(BotUpdateDto botUpdate, Long userId) {
        String userFirstName = botUpdate.message().from().firstName();
        String userUsername = botUpdate.message().from().username();
        String operatorName;

        if (userFirstName != null && !userFirstName.isEmpty()) {
            // 优先使用 firstName
            operatorName = userFirstName;
            if (userUsername != null && !userUsername.isEmpty()) {
                // 如果 username 存在，进行组合：firstName (@username)
                operatorName += " (@" + userUsername + ")";
            }
        } else if (userUsername != null && !userUsername.isEmpty()) {
            // 如果 firstName 缺失，仅使用 username
            operatorName = "@" + userUsername;
        } else {
            // 如果两者都没有，使用 userId 作为后备
            operatorName = String.valueOf(userId);
        }

        return operatorName;
    }

    /**
     * 处理等待 IP 和目标标识输入的逻辑。
     */
    private Mono<Void> handleAwaitingIpInput(String token, Long chatId, Long userId, String userText, UserSessionEntity session, String operatorName) {
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

        log.info("✅ Parsed input for {}. IP: {}, Target ID: {}, Operator: {}", domainType, ip, targetIdentifier, operatorName);

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


        // 4. 【优化后的顺序】先等待加白操作结果，然后无论结果如何，都清除会话状态，最后发送结果。
        return whitelistResultMono
                // 使用 doFinally 确保在 Mono 终止时（无论成功还是失败）都清除会话
                .doFinally(signalType -> {
                    // 确保在 Mono 结束时执行清理操作，防止会话残留
                    userSessionService.clearUserSession(userId).subscribe();
                })
                // 将最终的消息文本发送给用户
                .flatMap(resultText -> botClientService.sendMessage(token, chatId, resultText, null))
                .then();
    }

    /**
     * 处理普通文本输入，例如 /start 或未处于会话状态时的消息。
     */
    private Mono<Void> handleDefaultText(String token, Long chatId, String userText, String logIdentifier) {
        if (userText.startsWith("/start")) {
            // 重新发送主菜单逻辑（假设您有 SendMainMenu 方法）
            log.info("💬 {} Received /start command. Triggering main menu.", logIdentifier);
            // 假设 main menu 逻辑在 BotClientService 或其他地方
            String welcomeText = "✨✨✨ 选择服务: \uD83D\uDC47\uD83D\uDC47";
            // 这里应该调用一个发送主菜单的方法，此处简化为发送文本
            return botClientService.sendMessage(token, chatId, welcomeText, null).then();
        }

        // 修复：忽略其他普通文本，不再回复任何提示。
        log.debug("🤫 {} Ignoring non-session text: {}", logIdentifier, userText);
        return Mono.empty();
    }

    // 辅助方法：将状态映射回域名类型
    private String getDomainType(String state) {
        return switch (state) {
            case STATE_AWAITING_FRONTEND_WEB_IP -> "前端前台域名";
            case STATE_AWAITING_FRONTEND_ADMIN_IP -> "前端后台域名";
//            case STATE_AWAITING_MIDDLEWARE_IP -> "中间件域名";
            default -> "未知";
        };
    }
}