package com.backend.bot.handler;

import com.backend.bot.constants.TelegramConstants;
import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.dto.UserDto;
import com.backend.bot.entity.BotConfigEntity;
import com.backend.bot.entity.UserSessionEntity;
import com.backend.bot.service.BotClientService;
import com.backend.bot.service.GroupProjectService;
import com.backend.bot.service.UserSessionService;
import com.backend.bot.service.WhitelistService;
import com.backend.bot.service.InteractiveMessageService;
import com.backend.bot.util.BotUserUtils;
import com.backend.bot.util.LogUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import java.util.Map;
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
    private final GroupProjectService groupProjectService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final org.springframework.data.redis.core.ReactiveStringRedisTemplate redisTemplate;
    private final InteractiveMessageService interactiveMessageService;

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
            final Long userMessageId = botUpdate.message().messageId();

            // --- 优化点：使用 BotUserUtils 提取 User 和 OperatorName ---
            final UserDto user = BotUserUtils.extractUser(botUpdate)
                    .orElseThrow(() -> new IllegalStateException("User object is missing in a supported text update."));
            final Long userId = user.id();
            final String finalOperatorName = BotUserUtils.getOperatorName(user, userId);
            // --------------------------------------------------------

            // 1. 获取用户当前会话状态
            return userSessionService.getUserSession(userId, botName)
                    .flatMap(session -> {
                        // 统一使用 session.getState()
                        String state = session.getState();
                        log.info("{}📝 {} User {} (Op: {}) current state is: {}", traceLogPrefix, logIdentifier, userId, finalOperatorName, state);

                        // 2. 根据状态进行分发处理
                        if (state.equals(STATE_AWAITING_FRONTEND_WEB_IP) || state.equals(STATE_AWAITING_FRONTEND_ADMIN_IP)) {
                            return handleAwaitingIpInput(token, chatId, userId, userText, session, finalOperatorName, traceLogPrefix);
                        } else if (state.startsWith(WhitelistIpHandler.STATE_AWAITING_WHITELIST_IP_PREFIX)) {
                            return handleWhitelistIpInput(token, chatId, userId, userMessageId, userText, state, finalOperatorName, botEntity.getBotName(), traceLogPrefix);
                        } else if (state.equals(TelegramConstants.SESSION_STATE_PROCESSING_START)) {
                            return handleProcessingStart(token, chatId, logIdentifier, traceLogPrefix);
                        } else {
                            return handleDefaultText(token, chatId, userText, logIdentifier, traceLogPrefix);
                        }
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

        // 假设 UserSessionEntity 有 getPromptMessageId() 方法来获取需要删除的消息ID
        final Long promptMessageId = session.getPromptMessageId();

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


        // 4. 【修复后的顺序】先等待加白操作结果，然后执行**链式**清理旧消息和会话，最后发送结果。
        return whitelistResultMono
                // FIX: 使用 flatMap 接收结果文本
                .flatMap(resultText -> {
                    // 1. **链式**执行取消待删除任务操作
                    Mono<Void> cleanupMono = userSessionService.cancelPendingDeletion(userId)

                            // 2. (NEW) 立即删除原始的提示消息
                            .then(
                                    // 检查消息ID是否有效
                                    promptMessageId != null && promptMessageId > 0
                                            ? botClientService.deleteMessage(token, chatId, promptMessageId)
                                            .onErrorResume(e -> {
                                                // 删除失败时只记录警告，不中断主流程
                                                log.warn("{}⚠️ Failed to delete prompt message {} after successful operation. Reason: {}",
                                                        traceLogPrefix, promptMessageId, e.getMessage());
                                                return Mono.empty();
                                            })
                                            : Mono.empty()
                            )
                            // 3. 然后 (then) 执行会话清除操作。
                            .then(userSessionService.clearUserSession(userId));

                    // 4. 然后 (then) 链式执行发送成功/失败消息操作。
                    return cleanupMono
                            .then(botClientService.sendMessage(token, chatId, resultText, null));
                })
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

    /**
     * 处理白名单 IP 输入。格式: IP 用户名 或 IP+用户名
     * state 格式: AWAITING_WHITELIST_IP:{ruleId}:{env}
     */
    private Mono<Void> handleWhitelistIpInput(String token, Long chatId, Long userId, Long userMessageId, String userText,
                                                  String state, String operatorName, String botName, String traceLogPrefix) {
        String[] parts = state.replace(WhitelistIpHandler.STATE_AWAITING_WHITELIST_IP_PREFIX, "").split(":");
        if (parts.length < 2) {
            log.error("{}❌ Invalid whitelist session state: {}", traceLogPrefix, state);
            return botClientService.sendMessage(token, chatId, "⚠️ 会话状态异常，请重试", null).then();
        }
        String ruleId = parts[0];
        String env = parts[1];

        // Parse input: IP 用户名 (space or + separated)
        String[] inputParts = userText.trim().split("[\\s+]+", 2);
        if (inputParts.length < 1 || inputParts[0].isEmpty()) {
            return botClientService.sendMessage(token, chatId, "⚠️ 格式错误，请输入: IP 用户名\n例如: 1.2.3.4 张三", null).then();
        }
        String ip = inputParts[0];
        String username = inputParts.length > 1 ? inputParts[1] : "";

        // Validate IP format (IPv4 or IPv4/CIDR)
        if (!ip.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}(/\\d{1,2})?$")) {
            return botClientService.sendMessage(token, chatId, "⚠️ IP 格式不正确: " + ip + "\n请输入有效的 IPv4 地址，例如: 1.2.3.4 或 1.2.3.4/32", null)
                    .then(userSessionService.clearUserSession(userId))
                    .then();
        }

        log.info("{}✅ Whitelist input parsed. IP: {}, Username: {}, RuleId: {}, Env: {}, Operator: {}",
                traceLogPrefix, ip, username, ruleId, env, operatorName);

        return groupProjectService.getProjectId(botName, chatId, userId)
                .flatMap(projectId -> {
                    return whitelistService.addCfWhitelistIp(projectId, ruleId, ip, username, env, operatorName)
                            .flatMap(result -> {
                                String icon = result.startsWith("失败") ? "❌" : "✅";
                                String text = icon + " " + result + "\n\nIP: " + ip + " | 用户: " + (username.isEmpty() ? "未填写" : username) + "\n操作人: " + operatorName + "\n\n⏳ 此消息将在 30 秒后自动销毁";
                                // 删除用户发送的原始消息 + 发送回复 + 调度删除
                                return botClientService.deleteMessage(token, chatId, userMessageId)
                                        .onErrorResume(e -> Mono.empty())
                                        .then(userSessionService.clearUserSession(userId))
                                        .then(botClientService.sendMenuMessageWithResponse(token, chatId, text, null))
                                        .flatMap(resp -> {
                                        try {
                                                log.warn("🔍 Parsing sendMessage response: resp={}", resp);
                                                Map<String, Object> respMap = objectMapper.readValue(resp, Map.class);
                                                Object resultObj = respMap.get("result");
                                                log.warn("🔍 resultObj={}, type={}", resultObj, resultObj != null ? resultObj.getClass().getSimpleName() : "null");
                                                if (!(resultObj instanceof Map)) {
                                                    log.warn("⚠️ sendMessage result is not a Map: {}", resultObj);
                                                    return Mono.empty();
                                                }
                                                Map<String, Object> res = (Map<String, Object>) resultObj;
                                                Object msgIdObj = res.get("message_id");
                                                log.warn("🔍 msgIdObj={}, type={}", msgIdObj, msgIdObj != null ? msgIdObj.getClass().getSimpleName() : "null");
                                                if (msgIdObj == null) {
                                                    log.warn("⚠️ No message_id in response: {}", resp);
                                                    return Mono.empty();
                                                }
                                                Long msgId = Long.valueOf(String.valueOf(msgIdObj));
                                                log.debug("🔍 Scheduling deletion for msgId={}", msgId);
                                                return interactiveMessageService.scheduleMessageDeletion(
                                                        token, null, chatId, msgId, 30, "WhitelistAdd", com.backend.bot.util.LogUtils.buildTraceContext()
                                                ).then(Mono.empty());
                                            } catch (Exception e) {
                                                log.warn("⚠️ Failed to schedule message deletion: {}", e.getMessage(), e);
                                                return Mono.empty();
                                            }
                                        });
                            });
                })
                .onErrorResume(e -> {
                    log.error("{}❌ Whitelist add error: {}", traceLogPrefix, e.getMessage());
                    String errText = "⚠️ 加白失败: " + e.getMessage() + "\n\n⏳ 此消息将在 30 秒后自动销毁";
                    // 删除用户发送的原始消息 + 发送回复 + 调度删除
                    return botClientService.deleteMessage(token, chatId, userMessageId)
                            .onErrorResume(ex -> Mono.empty())
                            .then(userSessionService.clearUserSession(userId))
                            .then(botClientService.sendMenuMessageWithResponse(token, chatId, errText, null))
                            .flatMap(resp -> {
                                try {
                                    Map<String, Object> respMap = objectMapper.readValue(resp, Map.class);
                                    Map<String, Object> res = (Map<String, Object>) respMap.get("result");
                                    Long msgId = Long.valueOf(String.valueOf(res.get("message_id")));
                                    return interactiveMessageService.scheduleMessageDeletion(
                                            token, null, chatId, msgId, 30, "WhitelistAdd", com.backend.bot.util.LogUtils.buildTraceContext()
                                    ).then(Mono.empty());
                                } catch (Exception ex) {
                                    log.warn("⚠️ Failed to schedule message deletion: {}", ex.getMessage());
                                    return Mono.empty();
                                }
                            })
                            .then();
                })
                .then();
    }
}
