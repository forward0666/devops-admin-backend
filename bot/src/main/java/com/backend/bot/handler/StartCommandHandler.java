package com.backend.bot.handler;

import com.backend.bot.constants.TelegramConstants;
import com.backend.bot.context.HandlerContext;
import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.dto.InlineKeyboardMarkupDto;
import com.backend.bot.service.BotClientService;
import com.backend.bot.service.InteractiveMessageService;
import com.backend.bot.service.RedisUserSessionService;
import com.backend.bot.service.UserSessionService;
import com.backend.bot.service.BotMenuService;
import com.backend.bot.template.MenuType;
import com.backend.bot.repository.BotGroupProjectRepository;
import com.backend.bot.entity.BotGroupProjectEntity;
import com.backend.bot.util.BotUserUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(1)
public class StartCommandHandler extends AbstractUpdateHandler {

    private final BotClientService botClientService;
    private final InteractiveMessageService interactiveMessageService;
    private final UserSessionService userSessionService;
    private final RedisUserSessionService redisUserSessionService;
    private final ObjectMapper objectMapper;
    private final BotMenuService botMenuService;
    private final BotGroupProjectRepository botGroupProjectRepository;
    private final WebClient.Builder webClientBuilder;

    @Value("${bot.user-service-url:http://192.168.86.9:8084}")
    private String userServiceUrl;

    private static final String WELCOME_TEXT = TelegramConstants.WELCOME_MESSAGE;
    private static final int DELETE_DELAY_SECONDS = TelegramConstants.DEFAULT_DELETE_DELAY_SECONDS;

    // 🌟 警告消息常量
    private static final String WARNING_TEXT = "⚠️ 您有一个正在进行的操作，请完成当前操作或发送 /new 发起新请求。";
    private static final InlineKeyboardMarkupDto EMPTY_MENU = new InlineKeyboardMarkupDto(java.util.List.of());

    @Override
    public boolean support(BotUpdateDto update) {
        if (update.message() == null || update.message().text() == null) return false;
        String text = update.message().text().trim();
        Long chatId = update.message().chat().id();
        // 私聊：允许纯 /start
        if (chatId != null && chatId.equals(update.message().from().id())) {
            return text.startsWith("/start");
        }
        // 群聊：/start@xxx 或 纯 @xxx 开头
        return text.startsWith("/start") || text.startsWith("@");
    }

    @Override
    protected Mono<Void> handleUpdate(HandlerContext context, String logPrefix, ContextView contextView) {
        String token = context.token();
        Long chatId = context.chatId();
        Long userId = context.userId();

        // 🌟 优化点：直接调用工具类获取标准化的身份日志
        String identityLog = BotUserUtils.formatIdentityLog(context);

        log.info("{}✅ [Step1] 开始处理请求 | userId={}, chatId={}", logPrefix, userId, chatId);

        // 群聊：验证 @mention 匹配当前 bot
        String text = context.update().message().text().trim();
        if (text.contains("@")) {
            String mention = text.split("@")[1].split("\\s")[0].toLowerCase();
            if (!mention.equals(context.botName().toLowerCase())) {
                log.info("{}⚠️ @{} does not match current bot {}, ignoring", logPrefix, mention, context.botName());
                return Mono.empty();
            }
        }

        // 群聊场景：把用户的 /start 消息也加入定时删除
        Long userMessageId = context.messageId();
        if (userMessageId != null && chatId != null && chatId < 0) {
            interactiveMessageService.scheduleMessageDeletion(token, userId, chatId, userMessageId, DELETE_DELAY_SECONDS, null, contextView)
                    .subscribe();
        }

        // 检查 Redis 中是否存在用户会话标记（UserSession:1 或 UserSession:0）
        return redisUserSessionService.hasAnySession(userId)
                .doOnNext(hasSession -> log.info("{}🔍 [Step2] Session检查完成 | hasSession={}, userId={}", logPrefix, hasSession, userId))
                .flatMap(hasSession -> {
                    if (hasSession) {
                        // 用户有会话标记，说明用户当前正在进行操作
                        log.info("{}⚠️ User {} has an active session. Notifying user to complete current operation first.", logPrefix, userId);

                        // 🌟 核心修改：发送警告消息并调度自动删除
                        return botClientService.sendMenuMessageWithResponse(token, chatId, WARNING_TEXT, null)
                                .flatMap(responseJson -> {
                                    // 尝试解析响应 JSON 并调度删除
                                    try {
                                        // 使用 readTree() 解析 JSON 字符串
                                        JsonNode root = objectMapper.readTree(responseJson);
                                        JsonNode resultNode = root.path("result");

                                        if (!root.path("ok").asBoolean() || resultNode.isMissingNode()) {
                                            log.error("{}❌ API failure for warning message. JSON: {}", logPrefix, root.toPrettyString());
                                            return Mono.empty();
                                        }

                                        Long messageId = resultNode.path("message_id").asLong(0);
                                        if (messageId != 0) {
                                            log.info("{}⏳ Scheduling warning message deletion (ID: {}) in {} seconds.", logPrefix, messageId, DELETE_DELAY_SECONDS);

                                            // 调度删除任务
                                            // 修复：对非交互式警告消息，将 userId 设为 0L，防止 InteractiveMessageService 意外地清除用户的活跃会话。
                                            return interactiveMessageService.scheduleMessageDeletion(
                                                            token, 0L, chatId, messageId,
                                                            DELETE_DELAY_SECONDS, context.logIdentifier(), contextView
                                                    )
                                                    .onErrorResume(e -> {
                                                        log.warn("{}⚠️ Failed to schedule warning message deletion: {}", logPrefix, e.getMessage());
                                                        return Mono.empty(); // 调度失败不影响主流程
                                                    });
                                        }
                                    } catch (Exception e) {
                                        // 捕获 Jackson 转换错误
                                        log.error("{}❌ JSON/Object conversion error for warning message: {}", logPrefix, responseJson, e);
                                    }
                                    return Mono.empty(); // 如果解析或消息 ID 缺失，返回 Mono.empty()
                                })
                                .then(); // 转换为 Mono<Void>

                    } else {
                        // 用户没有会话标记，创建新会话
                        log.info("{}✅ [Step3] 无session，开始创建菜单 | userId={}", logPrefix, userId);
                        return checkGroupMembership(context, logPrefix)
                                .flatMap(allowed -> {
                                    if (!allowed) return Mono.empty();
                                    return processStartCommand(context, logPrefix, contextView)
                                .doOnSuccess(v -> log.info("{}✅ [StartCommand] 流程完成 | userId={}, menu已发送", logPrefix, userId))
                                .doOnError(e -> log.error("{}❌ [StartCommand] 流程失败 | userId={}, error={}", logPrefix, userId, e.getMessage()));
                                });
                    }
                });
    }

    /**
     * 处理/start命令的实际逻辑
     */
    private Mono<Void> processStartCommand(HandlerContext context, String logPrefix, ContextView contextView) {
        String token = context.token();
        Long chatId = context.chatId();
        Long userId = context.userId();

        // 先设置 session，再查菜单（避免 Mono.zip 空 empty 导致卡死）
        return userSessionService.updateUserSession(userId, TelegramConstants.SESSION_STATE_PROCESSING_START, null)
                .then(botMenuService.findMainMenuByBotType(context.botEntity().getBotType().getDbValue(), 1))
                        .switchIfEmpty(Mono.fromCallable(() -> MenuType.createFallbackKeyboard(context.botEntity().getBotType().name())))
                        .defaultIfEmpty(EMPTY_MENU)
                .flatMap(mainMenuMarkup -> {
                    if (mainMenuMarkup == EMPTY_MENU) {
                        log.warn("{}⚠️ No menu found for bot={} in DB or fallback", logPrefix, context.botName());
                        return userSessionService.clearUserSession(userId).then();
                    }
                    String mention = context.username() != null ? "@" + context.username() : (context.firstName() != null ? context.firstName() : "");
                    String welcomeText = "欢迎使用运维助手 " + mention + ".\n✨✨✨ 选择服务: 👇👇👇👇👇👇";
                    return botClientService.sendMenuMessageWithResponse(token, chatId, welcomeText, mainMenuMarkup, context.chatTitle())
                    .doOnNext(responseJson -> handleSendResponse(responseJson, token, userId, chatId, logPrefix, contextView, context))
                    .doOnError(e -> {
                        log.error("{}❌ Failed to send initial menu message.", logPrefix, e);
                        userSessionService.clearUserSession(userId).contextWrite(reactor.util.context.Context.of(contextView)).subscribe();
                    })
                    .then();
        });
    }

    /**
     * 群聊中检查用户是否是项目成员（私聊跳过检查）
     */
    private Mono<Boolean> checkGroupMembership(HandlerContext context, String logPrefix) {
        Long chatId = context.chatId();
        if (chatId >= 0) return Mono.just(true);

        String tgUsername = context.username();
        String mention = tgUsername != null ? "@" + tgUsername : "";
        String rejectMsg = "⚠️ 您不是该项目成员，无权限查看，" + (mention.isEmpty() ? "且未设置用户名。" : mention + "。");

        if (tgUsername == null || tgUsername.isBlank()) {
            log.warn("{}⚠️ User {} has no tg username, rejecting /start in group", logPrefix, context.userId());
            scheduleDeleteReply(context.token(), chatId, rejectMsg);
            return Mono.just(false);
        }

        return botGroupProjectRepository.findByBotNameAndChatId(context.botName(), chatId)
                .flatMap(binding -> {
                    WebClient webClient = webClientBuilder.baseUrl(userServiceUrl).build();
                    return webClient.get()
                            .uri("/projectMember?projectId={projectId}", binding.getProjectId())
                            .header("X-Tg-Username", tgUsername)
                            .retrieve()
                            .bodyToMono(Map.class)
                            .map(response -> {
                                Object code = response.get("code");
                                if (code != null && !"200".equals(String.valueOf(code)) && !"201".equals(String.valueOf(code))) {
                                    return false;
                                }
                                Object dataObj = response.get("data");
                                java.util.List<Map<String, Object>> members;
                                if (dataObj instanceof java.util.List) {
                                    members = (java.util.List<Map<String, Object>>) dataObj;
                                } else if (dataObj instanceof Map) {
                                    Object inner = ((Map<String, Object>) dataObj).get("data");
                                    members = (inner instanceof java.util.List) ? (java.util.List<Map<String, Object>>) inner : java.util.List.of();
                                } else {
                                    members = java.util.List.of();
                                }
                                boolean isMember = members.stream()
                                        .anyMatch(m -> tgUsername.equalsIgnoreCase(String.valueOf(m.getOrDefault("tgUsername", ""))));
                                if (!isMember) {
                                    log.info("{}⚠️ User {} (@{}) is not project member, rejecting /start", logPrefix, context.userId(), tgUsername);
                                    scheduleDeleteReply(context.token(), chatId, rejectMsg);
                                }
                                return isMember;
                            })
                            .onErrorReturn(false);
                })
                .defaultIfEmpty(true);
    }

    /**
     * 💡 额外建议：将解析 Response 的逻辑也提取为私有方法，让 handleUpdate 更清晰
     */
    private void handleSendResponse(String responseJson, String token, Long userId, Long chatId, String logPrefix, ContextView contextView, HandlerContext context) {
        log.debug("{}🔍 Received Telegram sendMessage response JSON: {}", logPrefix, responseJson);
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode resultNode = root.path("result");

            if (!root.path("ok").asBoolean() || resultNode.isMissingNode()) {
                log.error("{}❌ API failure. JSON: {}", logPrefix, responseJson);
                return;
            }

            Long messageId = resultNode.path("message_id").asLong(0);
            if (messageId != 0) {
                interactiveMessageService.scheduleMessageDeletion(
                        token, userId, chatId, messageId,
                        DELETE_DELAY_SECONDS, context.logIdentifier(), contextView
                ).subscribe();
            } else {
                log.warn("{}⚠️ Message ID is 0.", logPrefix);
            }
        } catch (Exception e) {
            log.error("{}❌ JSON parse error: {}", logPrefix, responseJson, e);
        }
    }

    private void scheduleDeleteReply(String token, Long chatId, String text) {
        botClientService.sendMenuMessageWithResponse(token, chatId, text, null)
                .flatMap(respJson -> {
                    try {
                        com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(respJson);
                        Long msgId = root.path("result").path("message_id").asLong(0);
                        if (msgId != 0) {
                            return interactiveMessageService.scheduleMessageDeletion(token, 0L, chatId, msgId, 5, null, reactor.util.context.Context.empty());
                        }
                    } catch (Exception ignored) {}
                    return reactor.core.publisher.Mono.empty();
                }).subscribe();
    }
}