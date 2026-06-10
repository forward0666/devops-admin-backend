package com.backend.bot.handler;

import com.backend.bot.context.HandlerContext;
import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.dto.InlineKeyboardButtonDto;
import com.backend.bot.dto.InlineKeyboardMarkupDto;
import com.backend.bot.entity.BotConfigEntity;
import com.backend.bot.service.GroupProjectService;
import com.backend.bot.service.BotClientService;
import com.backend.bot.service.InteractiveMessageService;
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
public class CachePurgeHandler implements CallbackActionHandler {

    private final BotClientService botClientService;
    private final GroupProjectService groupProjectService;
    private final InteractiveMessageService interactiveMessageService;
    private final WebClient.Builder lbWebClientBuilder;
    private final WebClient.Builder webClientBuilder;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${bot.cloudflare-service-url:}")
    private String cloudflareServiceUrl;

    @Value("${bot.cloudflare-service-name:cloudflare}")
    private String cloudflareServiceName;

    @Value("${bot.user-service-url:}")
    private String userServiceUrl;

    @Value("${bot.user-service-name:user}")
    private String userServiceName;

    private String getUserBaseUrl() {
        return (userServiceUrl != null && !userServiceUrl.isBlank())
                ? userServiceUrl : "lb://" + userServiceName;
    }

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
        boolean match = callbackData.startsWith("callback_data_PROJECT_PURGECACHE_")
                || callbackData.startsWith("callback_data_PURGE_RULE_");
        return match;
    }

    @Override
    public int getOrder() {
        return 5;
    }

    @Override
    public Mono<Void> handle(BotConfigEntity botEntity, BotUpdateDto botUpdate) {
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


        return Mono.deferContextual(contextView -> {
            String prefix = LogUtils.prepareMdcAndGetPrefix(contextView);

            if (action.startsWith("PROJECT_PURGECACHE_")) {
                String env = action.replace("PROJECT_PURGECACHE_", "").replace("_ACTION", "");
                return handlePurgeCacheList(prefix, botName, chatId, messageId, token, env, userId);
            } else if (action.startsWith("PURGE_RULE_")) {
                // PURGE_RULE_{ruleId}_{env}
                String parts = action.replace("PURGE_RULE_", "");
                int lastUnderscore = parts.lastIndexOf("_");
                String ruleId = lastUnderscore > 0 ? parts.substring(0, lastUnderscore) : parts;
                String ruleEnv = lastUnderscore > 0 ? parts.substring(lastUnderscore + 1).toUpperCase() : "";
                return handlePurgeRule(prefix, botName, chatId, messageId, token, ruleId, tgUsername, ruleEnv, userId);
            }
            return Mono.empty();
        })
        .doOnError(e -> log.error("❌ CachePurgeHandler error: {}", e.getMessage(), e))
        .onErrorResume(e -> botClientService.sendMessage(token, chatId, "⚠️ 操作失败: " + e.getMessage()).then());
    }

    private Mono<Void> handlePurgeCacheList(String prefix, String botName, Long chatId,
                                              Long messageId, String token, String env, Long userId) {

        return Mono.zip(
                groupProjectService.getProjectId(botName, chatId, userId),
                Mono.just(getCloudflareBaseUrl())
        ).flatMap(tuple -> {
            Long projectId = tuple.getT1();
            String cfUrl = tuple.getT2();
            WebClient webClient = getBuilder(cfUrl).baseUrl(cfUrl).build();
            String uri = "/cacheRule?projectId=" + projectId + (env != null ? "&env=" + env : "");

            return webClient.get().uri(uri).retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .flatMap(response -> {
                        Object dataObj = response.get("data");
                        List<Map<String, Object>> rules = (dataObj instanceof List)
                                ? (List<Map<String, Object>>) dataObj : List.of();

                        if (rules.isEmpty()) {
                            return sendMsg(token, chatId, "📋 " + (env != null ? env : "全部") + " 暂无缓存规则", null);
                        }

                        InlineKeyboardMarkupDto markup = new InlineKeyboardMarkupDto();
                        for (Map<String, Object> rule : rules) {
                            String ruleId = String.valueOf(rule.get("id"));
                            String name = String.valueOf(rule.get("name"));
                            String url = String.valueOf(rule.getOrDefault("url", ""));
                            markup.addRow(new InlineKeyboardButtonDto(name, "callback_data_PURGE_RULE_" + ruleId + "_" + env.toLowerCase()));
                        }
                        return sendMsg(token, chatId, "🧹 " + (env != null ? env : "全部") + " 缓存规则👇👇👇👇👇👇👇", markup);
                    });
        })
        .onErrorResume(e -> {
            log.error("{}❌ CachePurge list error: {}", prefix, e.getMessage());
            return sendMsg(token, chatId, "⚠️ 获取缓存规则失败: " + e.getMessage(), null);
        });
    }

    private Mono<Void> handlePurgeRule(String prefix, String botName, Long chatId,
                                         Long messageId, String token, String ruleId, String tgUsername, String env, Long userId) {

        return Mono.zip(
                groupProjectService.getProjectId(botName, chatId, userId),
                Mono.just(getCloudflareBaseUrl())
        ).flatMap(tuple -> {
            Long projectId = tuple.getT1();
            String cfUrl = tuple.getT2();
            WebClient cfClient = getBuilder(cfUrl).baseUrl(cfUrl).build();
            WebClient userClient = getBuilder(getUserBaseUrl()).baseUrl(getUserBaseUrl()).build();

            String uri = "/domain/list?projectId=" + projectId + (env != null && !env.isEmpty() ? "&env=" + env : "");
            log.info("{}🔍 CachePurgeRule: userBaseUrl={}, uri={}, projectId={}, env={}", prefix, getUserBaseUrl(), uri, projectId, env);
            return userClient.get().uri(uri)
                    .header("X-Tg-Username", tgUsername != null ? tgUsername : "bot")
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .map(resp -> {
                        Object data = resp.get("data");
                        List<Map<String, Object>> domainList;
                        if (data instanceof List) {
                            domainList = (List<Map<String, Object>>) data;
                        } else if (data instanceof Map) {
                            Object inner = ((Map<String, Object>) data).get("data");
                            domainList = (inner instanceof List) ? (List<Map<String, Object>>) inner : List.of();
                        } else {
                            domainList = List.of();
                        }
                        log.info("{}🔍 CachePurgeRule: domain list size={}, raw={}", prefix, domainList.size(), domainList.size() > 0 ? domainList.stream().map(d -> d.get("type") + ":" + d.get("domainName")).toList() : "empty");
                        return domainList.stream()
                                .filter(d -> "web".equals(String.valueOf(d.get("type"))))
                                .map(d -> String.valueOf(d.getOrDefault("domain", d.get("domainName"))))
                                .toList();
                    })
                    .flatMap(domains -> {
                        log.debug("🔍 CachePurgeRule: web domains={}", domains);
                        if (domains.isEmpty()) {
                            return sendMsg(token, chatId, "⚠️ 无 web 类型域名", null);
                        }
                        Map<String, Object> body = Map.of("ruleId", ruleId, "domains", domains);
                        return cfClient.post().uri("/cacheRule/purge")
                                .bodyValue(body)
                                .retrieve()
                                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                                .flatMap(response -> {
                                    Map<String, Object> result = (Map<String, Object>) response.get("data");
                                    List<String> succeeded = (List<String>) result.getOrDefault("succeeded", List.of());
                                    List<Map<String, Object>> failed = (List<Map<String, Object>>) result.getOrDefault("failed", List.of());

                                    StringBuilder sb = new StringBuilder("🧹 清理结果\n\n");
                                    if (!succeeded.isEmpty()) {
                                        sb.append("✅ 成功:\n");
                                        for (String s : succeeded) {
                                            sb.append(" - ").append(s).append("\n");
                                        }
                                    }
                                    if (!failed.isEmpty()) {
                                        sb.append("❌ 失败:\n");
                                        for (Map<String, Object> f : failed) {
                                            sb.append(" - ").append(f.get("domain")).append(": ").append(f.get("reason")).append("\n");
                                        }
                                    }
                                    return sendMsg(token, chatId, sb.toString(), null);
                                });
                    });
        })
        .onErrorResume(e -> {
            log.error("{}❌ CachePurge rule error: {}", prefix, e.getMessage());
            return sendMsg(token, chatId, "⚠️ 清理失败: " + e.getMessage(), null);
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
                                token, null, chatId, msgId, 30, "CachePurgeHandler", com.backend.bot.util.LogUtils.buildTraceContext()
                        ).subscribe();
                    } catch (Exception e) {
                        log.warn("⚠️ sendMsg: failed to parse msgId: {}", e.getMessage());
                    }
                    return Mono.empty();
                })
                .then();
    }
}
