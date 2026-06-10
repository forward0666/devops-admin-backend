package com.backend.bot.handler;

import com.backend.bot.context.HandlerContext;
import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.dto.InlineKeyboardButtonDto;
import com.backend.bot.dto.InlineKeyboardMarkupDto;
import com.backend.bot.entity.BotConfigEntity;
import com.backend.bot.service.BotClientService;
import com.backend.bot.service.GroupProjectService;
import com.backend.bot.service.InteractiveMessageService;
import com.backend.bot.service.UserSessionService;
import com.backend.bot.util.LogUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(5)
public class WhitelistIpHandler implements CallbackActionHandler {

    private final BotClientService botClientService;
    private final InteractiveMessageService interactiveMessageService;
    private final UserSessionService userSessionService;
    private final GroupProjectService groupProjectService;
    private final WebClient.Builder lbWebClientBuilder;
    private final WebClient.Builder webClientBuilder;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${bot.cloudflare-service-url:}")
    private String cloudflareServiceUrl;

    @Value("${bot.cloudflare-service-name:cloudflare}")
    private String cloudflareServiceName;

    public static final String STATE_AWAITING_WHITELIST_IP_PREFIX = "AWAITING_WHITELIST_IP:";

    private String getCloudflareBaseUrl() {
        return (cloudflareServiceUrl != null && !cloudflareServiceUrl.isBlank())
                ? cloudflareServiceUrl : "lb://" + cloudflareServiceName;
    }

    private WebClient.Builder getBuilder(String url) {
        return url.startsWith("lb://") ? lbWebClientBuilder : webClientBuilder;
    }

    @Override
    public boolean supports(String callbackData) {
        if (callbackData == null) return false;
        return callbackData.startsWith("callback_data_PROJECT_WHITELIST_")
                || callbackData.startsWith("callback_data_WHITELIST_REMOVE_")
                || callbackData.startsWith("callback_data_WHITELIST_SELECT_");
    }

    @Override
    public int getOrder() {
        return 5;
    }

    @Override
    public Mono<Void> handle(BotConfigEntity botEntity, BotUpdateDto botUpdate) {
        log.info("========== WhitelistIpHandler START ==========");
        try {
            HandlerContext ctx = new HandlerContext(botEntity, botUpdate);
            String token = ctx.token();
            Long chatId = ctx.chatId();
            Long userId = ctx.userId();
            Long messageId = ctx.messageId();
            String botName = ctx.botName();
            String callbackData = botUpdate.callbackQuery().data();
            String action = callbackData.replace("callback_data_", "");
            String tgUsername = botUpdate.callbackQuery() != null && botUpdate.callbackQuery().from() != null
                    ? botUpdate.callbackQuery().from().username() : "bot";

            log.info("[INPUT] action={}, chatId={}, botName={}, tgUsername={}, userId={}", action, chatId, botName, tgUsername, userId);

            return Mono.deferContextual(contextView -> {
                String prefix = LogUtils.prepareMdcAndGetPrefix(contextView);

                if (action.startsWith("PROJECT_WHITELIST_")) {
                    String env = action.replace("PROJECT_WHITELIST_", "").replace("_ACTION", "");
                    log.info("[STEP 1] PROJECT_WHITELIST -> env={}", env);
                    return handleSecurityRulesList(prefix, botName, chatId, messageId, token, env, userId);
                } else if (action.startsWith("WHITELIST_SELECT_")) {
                    String parts = action.replace("WHITELIST_SELECT_", "");
                    int lastUnderscore = parts.lastIndexOf("_");
                    String ruleId = lastUnderscore > 0 ? parts.substring(0, lastUnderscore) : parts;
                    String env = lastUnderscore > 0 ? parts.substring(lastUnderscore + 1) : "";
                    log.info("[STEP 1] WHITELIST_SELECT -> ruleId={}, env={}", ruleId, env);
                    return handleSelectRule(prefix, token, chatId, userId, messageId, ruleId, env, botName);
                } else if (action.startsWith("WHITELIST_REMOVE_")) {
                    String parts = action.replace("WHITELIST_REMOVE_", "");
                    int lastUnderscore = parts.lastIndexOf("_");
                    String recordId = lastUnderscore > 0 ? parts.substring(0, lastUnderscore) : parts;
                    String env = lastUnderscore > 0 ? parts.substring(lastUnderscore + 1) : "";
                    log.info("[STEP 1] WHITELIST_REMOVE -> recordId={}, env={}", recordId, env);
                    return handleRemoveIp(prefix, botName, chatId, messageId, token, recordId, env, userId);
                }
                log.warn("[STEP 1] No matching action: {}", action);
                return Mono.empty();
            })
            .doOnError(e -> log.error("❌ WhitelistIpHandler error: {}", e.getMessage(), e))
            .onErrorResume(e -> {
                log.error("❌ WhitelistIpHandler onErrorResume: {}", e.getMessage(), e);
                return Mono.empty();
            });
        } catch (Exception e) {
            log.error("❌ WhitelistIpHandler handle() exception: {}", e.getMessage(), e);
            return Mono.empty();
        }
    }

    private Mono<Void> handleSecurityRulesList(String prefix, String botName, Long chatId,
                                                  Long messageId, String token, String env, Long userId) {
        log.info("[STEP 2] Fetching projectId for botName={}, chatId={}", botName, chatId);

        return groupProjectService.getProjectId(botName, chatId, userId).flatMap(projectId -> {
            log.info("[STEP 3] projectId={}", projectId);
            WebClient webClient = getBuilder(getCloudflareBaseUrl()).baseUrl(getCloudflareBaseUrl()).build();
            String uri = "/securityRules?projectId=" + projectId + (env != null && !env.isEmpty() ? "&env=" + env : "");
            log.info("[STEP 4] Calling {}", uri);

            return webClient.get().uri(uri).retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .flatMap(response -> {
                        Object dataObj = response.get("data");
                        List<Map<String, Object>> rules = (dataObj instanceof List)
                                ? (List<Map<String, Object>>) dataObj : List.of();
                        log.info("[STEP 5] Got {} security rules", rules.size());

                        if (rules.isEmpty()) {
                            return sendMsg(token, chatId, "📋 " + env + " 暂无安全规则，请先在前端创建", null);
                        }

                        InlineKeyboardMarkupDto markup = new InlineKeyboardMarkupDto();
                        for (Map<String, Object> rule : rules) {
                            String ruleId = String.valueOf(rule.get("id"));
                            String name = String.valueOf(rule.getOrDefault("name", "?"));
                            markup.addRow(new InlineKeyboardButtonDto(
                                    "🔐 " + name,
                                    "callback_data_WHITELIST_SELECT_" + ruleId + "_" + env.toLowerCase()
                            ));
                        }
                        markup.addRow(new InlineKeyboardButtonDto("↩️ 返回主菜单", "callback_data_MAIN_MENU"));
                        log.info("[STEP 6] Sending rules list to chat");
                        String base = "📋 " + env + "：选择要操作的规则";
                        int padLen = 20 - base.length();
                        String padding = padLen > 0 ? "👇".repeat(padLen) : "";
                        return sendMsg(token, chatId, base + padding, markup);
                    });
        })
        .onErrorResume(e -> {
            log.error("[ERROR] handleSecurityRulesList: {}", e.getMessage(), e);
            return sendMsg(token, chatId, "⚠️ 获取安全规则失败: " + e.getMessage(), null);
        });
    }

    private Mono<Void> handleSelectRule(String prefix, String token, Long chatId, Long userId,
                                           Long messageId, String ruleId, String env, String botName) {
        log.info("[STEP 2] Selected ruleId={}, env={}, userId={}, botName={}", ruleId, env, userId, botName);

        String sessionState = STATE_AWAITING_WHITELIST_IP_PREFIX + ruleId + ":" + env;
        log.info("[STEP 3] Setting session state: {}", sessionState);

        return userSessionService.updateUserSession(userId, sessionState, messageId, botName)
                .then(sendPromptMsg(token, chatId));
    }

    private Mono<Void> sendPromptMsg(String token, Long chatId) {
        String text = "📝 请回复此消息，输入以下格式：\n\n"
                + "`IP 用户名` 或 `IP+用户名`\n\n"
                + "例如: `1.2.3.4 张三` 或 `1.2.3.4+张三`\n\n"
                + "⏳ 此提示将在 60 秒后自动销毁";

        return botClientService.sendMenuMessageWithResponse(token, chatId, text, null)
                .flatMap(responseJson -> {
                    try {
                        Map<String, Object> resp = objectMapper.readValue(responseJson, Map.class);
                        Map<String, Object> result = (Map<String, Object>) resp.get("result");
                        Long msgId = Long.valueOf(String.valueOf(result.get("message_id")));
                        interactiveMessageService.scheduleMessageDeletion(
                                token, null, chatId, msgId, 60, "WhitelistIpHandler", com.backend.bot.util.LogUtils.buildTraceContext()
                        ).subscribe();
                    } catch (Exception e) {
                        log.warn("⚠️ sendPromptMsg: failed to parse msgId: {}", e.getMessage());
                    }
                    return Mono.empty();
                })
                .then();
    }

    private Mono<Void> handleWhitelistList(String prefix, String botName, Long chatId,
                                              Long messageId, String token, String env, Long userId) {
        log.info("[STEP 2] Listing whitelist for botName={}, env={}", botName, env);

        return groupProjectService.getProjectId(botName, chatId, userId).flatMap(projectId -> {
            log.info("[STEP 3] projectId={}", projectId);
            WebClient webClient = getBuilder(getCloudflareBaseUrl()).baseUrl(getCloudflareBaseUrl()).build();
            String uri = "/whitelist?projectId=" + projectId + (env != null && !env.isEmpty() ? "&env=" + env : "");
            log.info("[STEP 4] Calling {}", uri);

            return webClient.get().uri(uri).retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .flatMap(response -> {
                        Object dataObj = response.get("data");
                        List<Map<String, Object>> records = (dataObj instanceof List)
                                ? (List<Map<String, Object>>) dataObj : List.of();
                        log.info("[STEP 5] Got {} whitelist records", records.size());

                        if (records.isEmpty()) {
                            return sendMsg(token, chatId, "📋 " + env + " 暂无白名单记录", null);
                        }

                        InlineKeyboardMarkupDto markup = new InlineKeyboardMarkupDto();
                        for (Map<String, Object> record : records) {
                            String id = String.valueOf(record.get("id"));
                            String ip = String.valueOf(record.getOrDefault("ip", "?"));
                            String username = String.valueOf(record.getOrDefault("username", ""));
                            String label = ip + (!username.isEmpty() ? " (" + username + ")" : "");
                            markup.addRow(new InlineKeyboardButtonDto(
                                    "❌ " + label,
                                    "callback_data_WHITELIST_REMOVE_" + id + "_" + env.toLowerCase()
                            ));
                        }
                        log.info("[STEP 6] Sending whitelist list to chat");
                        return sendMsg(token, chatId, "🔐 " + env + " 白名单 IP👇\n点击可删除", markup);
                    });
        })
        .onErrorResume(e -> {
            log.error("[ERROR] handleWhitelistList: {}", e.getMessage(), e);
            return sendMsg(token, chatId, "⚠️ 获取白名单失败: " + e.getMessage(), null);
        });
    }

    private Mono<Void> handleRemoveIp(String prefix, String botName, Long chatId,
                                         Long messageId, String token, String recordId, String env, Long userId) {
        log.info("[STEP 2] Removing recordId={}, botName={}", recordId, botName);

        return groupProjectService.getProjectId(botName, chatId, userId).flatMap(projectId -> {
            log.info("[STEP 3] projectId={}", projectId);
            WebClient webClient = getBuilder(getCloudflareBaseUrl()).baseUrl(getCloudflareBaseUrl()).build();
            String uri = "/whitelist/" + recordId + "?projectId=" + projectId;
            log.info("[STEP 4] Calling DELETE {}", uri);

            return webClient.delete().uri(uri).retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .flatMap(response -> {
                        String msg = String.valueOf(response.getOrDefault("message", "已删除"));
                        log.info("[STEP 5] Delete result: {}", msg);
                        return sendMsg(token, chatId, "✅ " + msg, null)
                                .then(handleWhitelistList(prefix, botName, chatId, messageId, token, env, userId));
                    });
        })
        .onErrorResume(e -> {
            log.error("[ERROR] handleRemoveIp: {}", e.getMessage(), e);
            return sendMsg(token, chatId, "⚠️ 删除失败: " + e.getMessage(), null);
        });
    }

    private Mono<Void> sendMsg(String token, Long chatId, String text, InlineKeyboardMarkupDto markup) {
        return botClientService.sendMenuMessageWithResponse(token, chatId, text, markup)
                .flatMap(responseJson -> {
                    try {
                        Map<String, Object> resp = objectMapper.readValue(responseJson, Map.class);
                        Map<String, Object> result = (Map<String, Object>) resp.get("result");
                        Long msgId = Long.valueOf(String.valueOf(result.get("message_id")));
                        interactiveMessageService.scheduleMessageDeletion(
                                token, null, chatId, msgId, 30, "WhitelistIpHandler", com.backend.bot.util.LogUtils.buildTraceContext()
                        ).subscribe();
                    } catch (Exception e) {
                        log.warn("⚠️ sendMsg: failed to parse msgId: {}", e.getMessage());
                    }
                    return Mono.empty();
                })
                .then();
    }
}
