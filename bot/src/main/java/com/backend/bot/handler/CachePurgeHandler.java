package com.backend.bot.handler;

import com.backend.bot.context.HandlerContext;
import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.dto.InlineKeyboardButtonDto;
import com.backend.bot.dto.InlineKeyboardMarkupDto;
import com.backend.bot.entity.BotConfigEntity;
import com.backend.bot.entity.BotGroupEntity;
import com.backend.bot.repository.BotGroupRepository;
import com.backend.bot.service.BotClientService;
import com.backend.bot.service.InteractiveMessageService;

import com.backend.bot.util.LogUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 处理缓存清理相关的 callback：
 * - PURGECACHE_{ENV} → 获取该环境的 cache rules，展示按钮
 * - PURGE_RULE_{ruleId} → 执行清理
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(5) // 低于 MenuNavigationHandler(10)，优先匹配
public class CachePurgeHandler implements CallbackActionHandler {

    private final BotClientService botClientService;
    private final BotGroupRepository botGroupRepository;
    private final InteractiveMessageService interactiveMessageService;
    private final WebClient.Builder webClientBuilder;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${bot.cloudflare-service-url:}")
    private String cloudflareServiceUrl;

    @Value("${bot.cloudflare-service-name:cloudflare}")
    private String cloudflareServiceName;

    private String getCloudflareBaseUrl() {
        return (cloudflareServiceUrl != null && !cloudflareServiceUrl.isBlank())
                ? cloudflareServiceUrl
                : "http://" + cloudflareServiceName;
    }
    private static final String USER_SERVICE_URL = "http://192.168.86.9:8084";
    private static final Duration CACHE_TTL = Duration.ofSeconds(60);

    @Override
    public boolean supports(String callbackData) {
        if (callbackData == null) return false;
        boolean match = callbackData.startsWith("callback_data_PROJECT_PURGECACHE_")
                || callbackData.startsWith("callback_data_PURGE_RULE_");
        log.info("🔍 CachePurgeHandler.supports({}) = {}", callbackData, match);
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
        Long messageId = ctx.messageId();
        String botName = ctx.botName();
        String callbackData = botUpdate.callbackQuery().data();
        String action = callbackData.replace("callback_data_", "");

        log.info("🚀🚀🚀 CachePurgeHandler ENTERED: callbackData={}, action={}, chatId={}, botName={}", callbackData, action, chatId, botName);

        return Mono.deferContextual(contextView -> {
            String traceLogPrefix = LogUtils.prepareMdcAndGetPrefix(contextView);

            if (action.startsWith("PROJECT_PURGECACHE_")) {
                log.info("🚀🚀🚀 CachePurgeHandler: matched PURGECACHE, env={}", action.replace("PROJECT_PURGECACHE_", "").replace("_ACTION", ""));
                // PROJECT_PURGECACHE_PROD_ACTION → PROD
                String env = action.replace("PROJECT_PURGECACHE_", "").replace("_ACTION", "");
                return handlePurgeCacheList(traceLogPrefix, botName, chatId, messageId, token, env);
            } else if (action.startsWith("PURGE_RULE_")) {
                String ruleId = action.replace("PURGE_RULE_", "");
                return handlePurgeRule(traceLogPrefix, botName, chatId, messageId, token, ruleId);
            }
            log.warn("⚠️ CachePurgeHandler: unhandled action={}", action);
            return Mono.empty();
        })
        .doOnError(e -> log.error("❌ CachePurgeHandler error: {}", e.getMessage(), e))
        .onErrorResume(e -> {
            log.error("❌ CachePurgeHandler onErrorResume: {}", e.getMessage());
            return botClientService.sendMessage(token, chatId, "⚠️ 操作失败: " + e.getMessage()).then();
        });
    }

    /**
     * 展示某环境的 cache rules 列表（按钮形式）
     */
    private Mono<Void> handlePurgeCacheList(String traceLogPrefix, String botName, Long chatId,
                                              Long messageId, String token, String env) {
        log.info("{}🔍 CachePurge: listing rules for botName={}, env={}", traceLogPrefix, botName, env);

        return Mono.zip(
                getProjectId(botName, chatId),
                Mono.just(getCloudflareBaseUrl())
        ).flatMap(tuple -> {
                    Long projectId = tuple.getT1();
                    String cfUrl = tuple.getT2();
                    log.info("{}🔍 CachePurge: projectId={}, cfUrl={}", traceLogPrefix, projectId, cfUrl);
                    WebClient webClient = webClientBuilder.baseUrl(cfUrl).build();
                    String uri = "/cacheRule?projectId=" + projectId + (env != null ? "&env=" + env : "");
                    log.info("{}🔍 CachePurge: fetching {}", traceLogPrefix, uri);

                    return webClient.get().uri(uri).retrieve()
                            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                            .flatMap(response -> {
                                Object dataObj = response.get("data");
                                List<Map<String, Object>> rules;
                                if (dataObj instanceof List) {
                                    rules = (List<Map<String, Object>>) dataObj;
                                } else {
                                    rules = List.of();
                                }

                                if (rules.isEmpty()) {
                                    return sendOrEdit(token, chatId, messageId, "📋 " + (env != null ? env : "全部") + " 暂无缓存规则", null);
                                }

                                // 构建按钮：每行一个规则
                                InlineKeyboardMarkupDto markup = new InlineKeyboardMarkupDto();
                                for (Map<String, Object> rule : rules) {
                                    String ruleId = String.valueOf(rule.get("id"));
                                    String name = String.valueOf(rule.get("name"));
                                    String url = String.valueOf(rule.getOrDefault("url", ""));
                                    String btnText = name + " " + url;
                                    markup.addRow(new InlineKeyboardButtonDto(btnText, "callback_data_PURGE_RULE_" + ruleId));
                                }

                                String text = "🧹 " + (env != null ? env : "全部") + " 缓存规则\n点击规则执行清理：";
                                return sendOrEdit(token, chatId, messageId, text, markup);
                            });
                })
                .onErrorResume(e -> {
                    log.error("{}❌ CachePurge list error: {}", traceLogPrefix, e.getMessage());
                    return sendOrEdit(token, chatId, messageId, "⚠️ 获取缓存规则失败: " + e.getMessage(), null);
                });
    }

    /**
     * 执行单条规则的缓存清理
     */
    private Mono<Void> handlePurgeRule(String traceLogPrefix, String botName, Long chatId,
                                         Long messageId, String token, String ruleId) {
        log.info("{}🔍 CachePurge: purging ruleId={} for botName={}", traceLogPrefix, ruleId, botName);

        return Mono.zip(
                getProjectId(botName, chatId),
                Mono.just(getCloudflareBaseUrl())
        ).flatMap(tuple -> {
                    Long projectId = tuple.getT1();
                    String cfUrl = tuple.getT2();
                    WebClient webClient = webClientBuilder.baseUrl(cfUrl).build();

                    Mono<List<String>> domainsMono = getWebDomains(projectId, traceLogPrefix);

                    return domainsMono.flatMap(domains -> {
                        if (domains.isEmpty()) {
                            return sendOrEdit(token, chatId, messageId, "⚠️ 无 web 类型域名", null);
                        }

                        Map<String, Object> body = Map.of("ruleId", ruleId, "domains", domains);
                        return webClient.post().uri("/cacheRule/purge")
                                .bodyValue(body)
                                .retrieve()
                                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                                .flatMap(response -> {
                                    Map<String, Object> result = (Map<String, Object>) response.get("data");
                                    List<String> succeeded = (List<String>) result.getOrDefault("succeeded", List.of());
                                    List<Map<String, Object>> failed = (List<Map<String, Object>>) result.getOrDefault("failed", List.of());

                                    StringBuilder sb = new StringBuilder();
                                    sb.append("🧹 清理结果\n\n");
                                    if (!succeeded.isEmpty()) {
                                        sb.append("✅ 成功: ").append(String.join(", ", succeeded)).append("\n");
                                    }
                                    if (!failed.isEmpty()) {
                                        sb.append("❌ 失败:\n");
                                        for (Map<String, Object> f : failed) {
                                            sb.append("  ").append(f.get("domain")).append(": ").append(f.get("reason")).append("\n");
                                        }
                                    }

                                    // 返回按钮：返回列表
                                    InlineKeyboardMarkupDto markup = new InlineKeyboardMarkupDto();
                                    markup.addRow(new InlineKeyboardButtonDto("🔙 返回列表", "callback_data_PURGECACHE_ALL_ACTION"));

                                    return sendOrEdit(token, chatId, messageId, sb.toString(), markup);
                                });
                    });
                })
                .onErrorResume(e -> {
                    log.error("{}❌ CachePurge rule error: {}", traceLogPrefix, e.getMessage());
                    return sendOrEdit(token, chatId, messageId, "⚠️ 清理失败: " + e.getMessage(), null);
                });
    }

    /**
     * 获取群组绑定的 projectId
     */
    private Mono<Long> getProjectId(String botName, Long chatId) {
        String cacheKey = "bot:groupProject:" + botName + ":" + chatId;
        log.info("🔍 getProjectId: botName={}, chatId={}, cacheKey={}", botName, chatId, cacheKey);
        return redisTemplate.opsForValue().get(cacheKey)
                .doOnNext(cached -> log.info("🔍 getProjectId: redis cached={}", cached))
                .flatMap(cached -> {
                    try {
                        BotGroupEntity entity = objectMapper.readValue(cached, BotGroupEntity.class);
                        log.info("🔍 getProjectId: parsed projectId={}", entity.getProjectId());
                        return Mono.just(entity.getProjectId());
                    } catch (Exception e) {
                        log.warn("⚠️ getProjectId: failed to parse cached value: {}", e.getMessage());
                        return Mono.empty();
                    }
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("🔍 getProjectId: redis miss, querying DB for botName={}, chatId={}", botName, chatId);
                    return botGroupRepository.findByBotNameAndChatId(botName, chatId)
                            .doOnNext(entity -> log.info("🔍 getProjectId: DB found projectId={}", entity.getProjectId()))
                            .flatMap(entity -> {
                                try {
                                    String json = objectMapper.writeValueAsString(entity);
                                    redisTemplate.opsForValue().set(cacheKey, json, Duration.ofSeconds(300)).subscribe();
                                } catch (Exception ignored) {}
                                return Mono.just(entity.getProjectId());
                            })
                            .switchIfEmpty(Mono.defer(() -> {
                                log.error("❌ CachePurge: no project binding for botName={}, chatId={}", botName, chatId);
                                return Mono.error(new RuntimeException("该群组未绑定项目"));
                            }));
                }));
    }

    /**
     * 获取项目的 web 类型域名列表
     */
    @SuppressWarnings("unchecked")
    private Mono<List<String>> getWebDomains(Long projectId, String traceLogPrefix) {
        return Mono.just(USER_SERVICE_URL).flatMap(userUrl -> {
        WebClient webClient = webClientBuilder.baseUrl(userUrl).build();
        return webClient.get().uri("/domain/list?projectId=" + projectId)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(response -> {
                    Object dataObj = response.get("data");
                    List<Map<String, Object>> domains;
                    if (dataObj instanceof List) {
                        domains = (List<Map<String, Object>>) dataObj;
                    } else if (dataObj instanceof Map) {
                        Object inner = ((Map<String, Object>) dataObj).get("data");
                        domains = (inner instanceof List) ? (List<Map<String, Object>>) inner : List.of();
                    } else {
                        domains = List.of();
                    }
                    log.info("🔍 getWebDomains: total={}, types={}", domains.size(),
                            domains.stream().map(d -> String.valueOf(d.get("type"))).distinct().toList());
                    return domains.stream()
                            .filter(d -> "web".equals(String.valueOf(d.get("type"))))
                            .map(d -> String.valueOf(d.get("domain")))
                            .toList();
                })
                .onErrorReturn(List.of());
        });
    }

    private Mono<Void> sendOrEdit(String token, Long chatId, Long messageId, String text, InlineKeyboardMarkupDto markup) {
        // Always send new message to avoid race condition with deletion timer
        return botClientService.sendMessage(token, chatId, text, markup)
                .then();
    }
}
