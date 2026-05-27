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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(5)
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
                ? cloudflareServiceUrl : "http://" + cloudflareServiceName;
    }

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
        String tgUsername = botUpdate.callbackQuery() != null && botUpdate.callbackQuery().from() != null
                ? botUpdate.callbackQuery().from().username() : "bot";

        log.info("🚀 CachePurgeHandler: action={}, chatId={}, botName={}, tgUsername={}", action, chatId, botName, tgUsername);

        return Mono.deferContextual(contextView -> {
            String prefix = LogUtils.prepareMdcAndGetPrefix(contextView);

            if (action.startsWith("PROJECT_PURGECACHE_")) {
                String env = action.replace("PROJECT_PURGECACHE_", "").replace("_ACTION", "");
                return handlePurgeCacheList(prefix, botName, chatId, messageId, token, env);
            } else if (action.startsWith("PURGE_RULE_")) {
                // PURGE_RULE_{ruleId}_{env}
                String parts = action.replace("PURGE_RULE_", "");
                int lastUnderscore = parts.lastIndexOf("_");
                String ruleId = lastUnderscore > 0 ? parts.substring(0, lastUnderscore) : parts;
                String ruleEnv = lastUnderscore > 0 ? parts.substring(lastUnderscore + 1) : "";
                return handlePurgeRule(prefix, botName, chatId, messageId, token, ruleId, tgUsername, ruleEnv);
            }
            return Mono.empty();
        })
        .doOnError(e -> log.error("❌ CachePurgeHandler error: {}", e.getMessage(), e))
        .onErrorResume(e -> botClientService.sendMessage(token, chatId, "⚠️ 操作失败: " + e.getMessage()).then());
    }

    private Mono<Void> handlePurgeCacheList(String prefix, String botName, Long chatId,
                                              Long messageId, String token, String env) {
        log.info("{}🔍 CachePurge: listing rules for botName={}, env={}", prefix, botName, env);

        return Mono.zip(
                getProjectId(botName, chatId),
                Mono.just(getCloudflareBaseUrl())
        ).flatMap(tuple -> {
            Long projectId = tuple.getT1();
            String cfUrl = tuple.getT2();
            WebClient webClient = webClientBuilder.baseUrl(cfUrl).build();
            String uri = "/cacheRule?projectId=" + projectId + (env != null ? "&env=" + env : "");
            log.info("{}🔍 CachePurge: fetching {}", prefix, uri);

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
                            String ruleEnv = String.valueOf(rule.getOrDefault("env", "")).toLowerCase();
                            markup.addRow(new InlineKeyboardButtonDto(name + " " + url, "callback_data_PURGE_RULE_" + ruleId + "_" + ruleEnv));
                        }
                        return sendMsg(token, chatId, "🧹 " + (env != null ? env : "全部") + " 缓存规则\n点击规则执行清理：", markup);
                    });
        })
        .onErrorResume(e -> {
            log.error("{}❌ CachePurge list error: {}", prefix, e.getMessage());
            return sendMsg(token, chatId, "⚠️ 获取缓存规则失败: " + e.getMessage(), null);
        });
    }

    private Mono<Void> handlePurgeRule(String prefix, String botName, Long chatId,
                                         Long messageId, String token, String ruleId, String tgUsername, String env) {
        log.info("{}🔍 CachePurge: purging ruleId={} for botName={}", prefix, ruleId, botName);

        return Mono.zip(
                getProjectId(botName, chatId),
                Mono.just(getCloudflareBaseUrl())
        ).flatMap(tuple -> {
            Long projectId = tuple.getT1();
            String cfUrl = tuple.getT2();
            WebClient cfClient = webClientBuilder.baseUrl(cfUrl).build();
            WebClient userClient = webClientBuilder.baseUrl("http://192.168.86.9:8084").build();

            String uri = "/domain/list?projectId=" + projectId + (env != null && !env.isEmpty() ? "&env=" + env : "");
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
                        return domainList.stream()
                                .filter(d -> "web".equals(String.valueOf(d.get("type"))))
                                .map(d -> String.valueOf(d.get("domain")))
                                .toList();
                    })
                    .flatMap(domains -> {
                        log.info("🔍 CachePurgeRule: web domains={}", domains);
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
                                        sb.append("✅ 成功: ").append(String.join(", ", succeeded)).append("\n");
                                    }
                                    if (!failed.isEmpty()) {
                                        sb.append("❌ 失败:\n");
                                        for (Map<String, Object> f : failed) {
                                            sb.append("  ").append(f.get("domain")).append(": ").append(f.get("reason")).append("\n");
                                        }
                                    }
                                    InlineKeyboardMarkupDto markup = new InlineKeyboardMarkupDto();
                                    markup.addRow(new InlineKeyboardButtonDto("🔙 返回列表", "callback_data_PURGECACHE_ALL_ACTION"));
                                    return sendMsg(token, chatId, sb.toString(), markup);
                                });
                    });
        })
        .onErrorResume(e -> {
            log.error("{}❌ CachePurge rule error: {}", prefix, e.getMessage());
            return sendMsg(token, chatId, "⚠️ 清理失败: " + e.getMessage(), null);
        });
    }

    private Mono<Long> getProjectId(String botName, Long chatId) {
        String cacheKey = "bot:groupProject:" + botName + ":" + chatId;
        return redisTemplate.opsForValue().get(cacheKey)
                .flatMap(cached -> {
                    try {
                        BotGroupEntity entity = objectMapper.readValue(cached, BotGroupEntity.class);
                        return Mono.just(entity.getProjectId());
                    } catch (Exception e) {
                        return Mono.empty();
                    }
                })
                .switchIfEmpty(botGroupRepository.findByBotNameAndChatId(botName, chatId)
                        .flatMap(entity -> {
                            try {
                                String json = objectMapper.writeValueAsString(entity);
                                redisTemplate.opsForValue().set(cacheKey, json, Duration.ofSeconds(300)).subscribe();
                            } catch (Exception ignored) {}
                            return Mono.just(entity.getProjectId());
                        })
                        .switchIfEmpty(Mono.error(new RuntimeException("该群组未绑定项目"))));
    }

    private Mono<Void> sendMsg(String token, Long chatId, String text, InlineKeyboardMarkupDto markup) {
        return botClientService.sendMessage(token, chatId, text, markup).then();
    }
}
