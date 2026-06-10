package com.backend.bot.handler;

import com.backend.bot.context.HandlerContext;
import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.dto.InlineKeyboardButtonDto;
import com.backend.bot.dto.InlineKeyboardMarkupDto;
import com.backend.bot.entity.BotConfigEntity;
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
import reactor.util.context.ContextView;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(4)
public class DevOpsProjectListHandler implements CallbackActionHandler {

    private final BotClientService botClientService;
    private final InteractiveMessageService interactiveMessageService;
    private final WebClient.Builder lbWebClientBuilder;
    private final WebClient.Builder webClientBuilder;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${bot.user-service-url:}")
    private String userServiceUrl;

    @Value("${bot.user-service-name:user}")
    private String userServiceName;

    private static final Duration SELECTED_PROJECT_TTL = Duration.ofMinutes(5);
    private static final Duration LIST_CACHE_TTL = Duration.ofSeconds(60);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {};

    private static final String PROJECT_LIST_ACTION = "PROJECT_LIST_ACTION";
    private static final String PROJECT_SELECT_PREFIX = "PROJECT_SELECT_";
    private static final String PROJECT_INFO_ACTION = "PROJECT_INFO_ACTION";
    private static final String PROJECT_MEMBER_ACTION = "PROJECT_MEMBER_ACTION";
    private static final String PROJECT_DOMAIN_ACTION = "PROJECT_DOMAIN_ACTION";
    private static final String PROJECT_DOMAIN_PROD_ACTION = "PROJECT_DOMAIN_PROD_ACTION";
    private static final String PROJECT_DOMAIN_UAT_ACTION = "PROJECT_DOMAIN_UAT_ACTION";
    private static final String PROJECT_DOMAIN_TEST_ACTION = "PROJECT_DOMAIN_TEST_ACTION";
    private static final String PROJECT_DOMAIN_DEV_ACTION = "PROJECT_DOMAIN_DEV_ACTION";
    private static final String PROJECT_PURGECACHE_PROD_ACTION = "PROJECT_PURGECACHE_PROD_ACTION";
    private static final String PROJECT_PURGECACHE_UAT_ACTION = "PROJECT_PURGECACHE_UAT_ACTION";
    private static final String PROJECT_PURGECACHE_TEST_ACTION = "PROJECT_PURGECACHE_TEST_ACTION";
    private static final String PROJECT_PURGECACHE_DEV_ACTION = "PROJECT_PURGECACHE_DEV_ACTION";
    private static final String PROJECT_WHITELIST_PROD_ACTION = "PROJECT_WHITELIST_PROD_ACTION";
    private static final String PROJECT_WHITELIST_TEST_ACTION = "PROJECT_WHITELIST_TEST_ACTION";
    private static final String PROJECT_MIDDLEWARE_ACTION = "PROJECT_MIDDLEWARE_ACTION";
    private static final String PURGECACHE_ACTION = "PURGECACHE_ACTION";
    private static final String WHITELIST_ACTION = "WHITELIST_ACTION";

    private String getUserBaseUrl() {
        return (userServiceUrl != null && !userServiceUrl.isBlank())
                ? userServiceUrl : "lb://" + userServiceName;
    }

    private WebClient.Builder getBuilder(String url) {
        return url.startsWith("lb://") ? lbWebClientBuilder : webClientBuilder;
    }

    @Override
    public boolean supports(String callbackData) {
        if (callbackData == null) return false;
        String cd = callbackData.replace("callback_data_", "");
        return cd.equals(PROJECT_LIST_ACTION)
                || cd.startsWith(PROJECT_SELECT_PREFIX)
                || cd.equals(PROJECT_INFO_ACTION)
                || cd.equals(PROJECT_MEMBER_ACTION)
                || cd.equals(PROJECT_MIDDLEWARE_ACTION);
    }

    @Override
    public boolean supports(String callbackData, BotConfigEntity botEntity) {
        if (botEntity.getBotType() != com.backend.bot.enums.BotType.DEVOPS) return false;
        return supports(callbackData);
    }

    @Override
    public int getOrder() {
        return 4;
    }

    @Override
    public Mono<Void> handle(BotConfigEntity botEntity, BotUpdateDto botUpdate) {
        try {
            // 非 DevOps 类型 bot 不处理，让其他 handler 接管
            if (botEntity.getBotType() != com.backend.bot.enums.BotType.DEVOPS) {
                return Mono.empty();
            }

            HandlerContext ctx = new HandlerContext(botEntity, botUpdate);
            String token = ctx.token();
            Long chatId = ctx.chatId();
            Long userId = ctx.userId();
            Long messageId = ctx.messageId();
            String callbackData = botUpdate.callbackQuery().data();
            String action = callbackData.replace("callback_data_", "");
            String tgUsername = botUpdate.callbackQuery() != null && botUpdate.callbackQuery().from() != null
                    ? botUpdate.callbackQuery().from().username() : "bot";

            return Mono.deferContextual(contextView -> {
                String prefix = LogUtils.prepareMdcAndGetPrefix(contextView);

                if (action.equals(PROJECT_LIST_ACTION)) {
                    return handleProjectList(prefix, token, chatId, messageId, tgUsername);
                } else if (action.startsWith(PROJECT_SELECT_PREFIX)) {
                    String projectIdStr = action.replace(PROJECT_SELECT_PREFIX, "");
                    return handleProjectSelect(prefix, token, chatId, userId, messageId, projectIdStr, tgUsername);
                } else {
                    String redisKey = "bot:devops:selectedProject:" + userId;
                    return redisTemplate.opsForValue().get(redisKey)
                            .flatMap(projectIdStr -> {
                                Long projectId = Long.valueOf(projectIdStr);
                                return handleSubAction(prefix, token, chatId, userId, messageId, action, projectId, tgUsername);
                            })
                            .switchIfEmpty(Mono.defer(() -> {
                                log.info("{}⚠️ No DevOps selected project for userId={}, action={}. Fallback.", prefix, userId, action);
                                return Mono.empty();
                            }));
                }
            })
            .onErrorResume(e -> {
                log.error("❌ DevOpsProjectListHandler error: {}", e.getMessage(), e);
                return Mono.empty();
            });
        } catch (Exception e) {
            log.error("❌ DevOpsProjectListHandler handle() exception: {}", e.getMessage(), e);
            return Mono.empty();
        }
    }

    private Mono<Void> handleProjectList(String prefix, String token, Long chatId, Long messageId, String tgUsername) {
        String cacheKey = "bot:devops:projectList";
        WebClient webClient = getBuilder(getUserBaseUrl()).baseUrl(getUserBaseUrl())
                .defaultHeader("X-Tg-Username", tgUsername != null ? tgUsername : "bot").build();

        return redisTemplate.opsForValue().get(cacheKey)
                .flatMap(cached -> {
                    try {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> projects = objectMapper.readValue(cached, List.class);
                        return Mono.just(projects);
                    } catch (Exception e) {
                        return Mono.<List<Map<String, Object>>>empty();
                    }
                })
                .switchIfEmpty(
                        webClient.get().uri("/project").retrieve()
                                .bodyToMono(MAP_TYPE)
                                .flatMap(response -> {
                                    Object dataObj = response.get("data");
                                    List<Map<String, Object>> projects;
                                    if (dataObj instanceof List) {
                                        projects = (List<Map<String, Object>>) dataObj;
                                    } else {
                                        projects = List.of();
                                    }
                                    try {
                                        redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(projects), LIST_CACHE_TTL).subscribe();
                                    } catch (Exception ignored) {}
                                    return Mono.just(projects);
                                })
                )
                .flatMap(projects -> {
                    if (projects.isEmpty()) {
                        return sendMsg(token, chatId, "📋 暂无项目", null);
                    }

                    InlineKeyboardMarkupDto markup = new InlineKeyboardMarkupDto();
                    for (Map<String, Object> project : projects) {
                        String id = String.valueOf(project.get("id"));
                        String name = String.valueOf(project.getOrDefault("name", id));
                        markup.addRow(new InlineKeyboardButtonDto("📁 " + name, "callback_data_PROJECT_SELECT_" + id));
                    }
                    markup.addRow(new InlineKeyboardButtonDto("↩️ 返回主菜单", "callback_data_MAIN_MENU"));

                    return sendMsg(token, chatId, "📋 选择项目👇", markup);
                })
                .onErrorResume(e -> {
                    log.error("{}❌ Failed to fetch project list: {}", prefix, e.getMessage(), e);
                    return sendMsg(token, chatId, "⚠️ 获取项目列表失败: " + e.getMessage(), null);
                });
    }

    private Mono<Void> handleProjectSelect(String prefix, String token, Long chatId, Long userId, Long messageId, String projectIdStr, String tgUsername) {
        String redisKey = "bot:devops:selectedProject:" + userId;
        return redisTemplate.opsForValue().set(redisKey, projectIdStr, SELECTED_PROJECT_TTL)
                .then(Mono.defer(() -> {
                    InlineKeyboardMarkupDto markup = new InlineKeyboardMarkupDto();
                    markup.addRow(new InlineKeyboardButtonDto("📋 项目信息", "callback_data_PROJECT_INFO_ACTION"));
                    markup.addRow(new InlineKeyboardButtonDto("👥 成员列表", "callback_data_PROJECT_MEMBER_ACTION"));
                    markup.addRow(new InlineKeyboardButtonDto("🌐 域名列表", "callback_data_PROJECT_DOMAIN_ACTION"));
                    markup.addRow(new InlineKeyboardButtonDto("🔧 中间件信息", "callback_data_PROJECT_MIDDLEWARE_ACTION"));
                    markup.addRow(new InlineKeyboardButtonDto("🧹 缓存清理", "callback_data_PURGECACHE_ACTION"));
                    markup.addRow(new InlineKeyboardButtonDto("🛡️ IP加白", "callback_data_WHITELIST_ACTION"));
                    markup.addRow(new InlineKeyboardButtonDto("↩️ 返回项目列表", "callback_data_PROJECT_LIST_ACTION"));

                    return sendMsg(token, chatId, "📁 已选择项目 #" + projectIdStr + "\n请选择操作👇", markup);
                }))
                .onErrorResume(e -> {
                    log.error("{}❌ handleProjectSelect error: {}", prefix, e.getMessage(), e);
                    return sendMsg(token, chatId, "⚠️ 操作失败", null);
                });
    }

    private Mono<Void> handleSubAction(String prefix, String token, Long chatId, Long userId, Long messageId,
                                        String action, Long projectId, String tgUsername) {
        log.info("{}🔍 DevOps sub-action: action={}, projectId={}, userId={}", prefix, action, projectId, userId);

        return switch (action) {
            case PROJECT_INFO_ACTION -> fetchProjectInfo(prefix, token, chatId, messageId, projectId, tgUsername);
            case PROJECT_MEMBER_ACTION -> fetchProjectMembers(prefix, token, chatId, messageId, projectId, tgUsername);
            case PROJECT_DOMAIN_ACTION -> fetchProjectDomains(prefix, token, chatId, messageId, projectId, null, tgUsername);
            case PROJECT_MIDDLEWARE_ACTION -> fetchProjectMiddlewares(prefix, token, chatId, messageId, projectId, tgUsername);
            case PURGECACHE_ACTION -> sendPurgeCacheMenu(token, chatId, projectId);
            case WHITELIST_ACTION -> sendWhitelistMenu(token, chatId, projectId);
            default -> Mono.empty();
        };
    }

    private Mono<Void> sendWhitelistMenu(String token, Long chatId, Long projectId) {
        InlineKeyboardMarkupDto markup = new InlineKeyboardMarkupDto();
        markup.addRow(new InlineKeyboardButtonDto("🔒 PROD", "callback_data_PROJECT_WHITELIST_PROD_ACTION"));
        markup.addRow(new InlineKeyboardButtonDto("🟡 UAT", "callback_data_PROJECT_WHITELIST_UAT_ACTION"));
        markup.addRow(new InlineKeyboardButtonDto("🟢 TEST", "callback_data_PROJECT_WHITELIST_TEST_ACTION"));
        markup.addRow(new InlineKeyboardButtonDto("🔵 DEV", "callback_data_PROJECT_WHITELIST_DEV_ACTION"));
        markup.addRow(new InlineKeyboardButtonDto("↩️ 返回", "callback_data_PROJECT_LIST_ACTION"));

        return sendMsg(token, chatId, "🛡️ 选择要加白的环境\n项目ID: " + projectId + "👇", markup);
    }

    private Mono<Void> sendPurgeCacheMenu(String token, Long chatId, Long projectId) {
        InlineKeyboardMarkupDto markup = new InlineKeyboardMarkupDto();
        markup.addRow(new InlineKeyboardButtonDto("🔒 PROD", "callback_data_PROJECT_PURGECACHE_PROD_ACTION"));
        markup.addRow(new InlineKeyboardButtonDto("🟡 UAT", "callback_data_PROJECT_PURGECACHE_UAT_ACTION"));
        markup.addRow(new InlineKeyboardButtonDto("🟢 TEST", "callback_data_PROJECT_PURGECACHE_TEST_ACTION"));
        markup.addRow(new InlineKeyboardButtonDto("🔵 DEV", "callback_data_PROJECT_PURGECACHE_DEV_ACTION"));
        markup.addRow(new InlineKeyboardButtonDto("↩️ 返回", "callback_data_PROJECT_LIST_ACTION"));

        return sendMsg(token, chatId, "🧹 选择要清理缓存的环境\n项目ID: " + projectId + "👇", markup);
    }

    private Mono<Void> fetchProjectInfo(String prefix, String token, Long chatId, Long messageId, Long projectId, String tgUsername) {
        String cacheKey = "bot:devops:project:" + projectId;
        WebClient webClient = getBuilder(getUserBaseUrl()).baseUrl(getUserBaseUrl())
                .defaultHeader("X-Tg-Username", tgUsername != null ? tgUsername : "bot").build();

        return cacheOrFetch(cacheKey, Duration.ofSeconds(60),
                webClient.get().uri("/project/{id}", projectId).retrieve())
                .flatMap(project -> {
                    Object code = project.get("code");
                    if (code != null && !"200".equals(String.valueOf(code)) && !"201".equals(String.valueOf(code))) {
                        return sendMsg(token, chatId, "⚠️ 查询失败：" + project.getOrDefault("message", "未知错误"), null);
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = project.containsKey("data") ? (Map<String, Object>) project.get("data") : project;

                    if (data == null) {
                        return sendMsg(token, chatId, "📋 项目ID: " + projectId + "\n⚠️ 暂无详细信息", null);
                    }

                    StringBuilder sb = new StringBuilder();
                    sb.append("📋 *项目信息*\n\n");
                    sb.append("项目名称：").append(getVal(data, "name", "")).append("\n");
                    sb.append("描述：").append(getVal(data, "description", "暂无")).append("\n");
                    sb.append("技术栈：").append(getVal(data, "techStack", "暂无")).append("\n");
                    sb.append("状态：").append(getVal(data, "status", "未知")).append("\n");
                    sb.append("进度：").append(getVal(data, "progress", 0)).append("%\n");
                    sb.append("创建时间：").append(getVal(data, "createdAt", "暂无")).append("\n");

                    return sendMsg(token, chatId, sb.toString(), null);
                })
                .onErrorResume(e -> {
                    log.warn("{}⚠️ Failed to fetch project info: {}", prefix, e.getMessage());
                    return sendMsg(token, chatId, "⚠️ 查询项目信息失败: " + e.getMessage(), null);
                });
    }

    private Mono<Void> fetchProjectMembers(String prefix, String token, Long chatId, Long messageId, Long projectId, String tgUsername) {
        String cacheKey = "bot:devops:members:" + projectId;
        WebClient webClient = getBuilder(getUserBaseUrl()).baseUrl(getUserBaseUrl())
                .defaultHeader("X-Tg-Username", tgUsername != null ? tgUsername : "bot").build();

        return cacheOrFetch(cacheKey, Duration.ofSeconds(60),
                webClient.get().uri("/projectMember?projectId={projectId}", projectId).retrieve())
                .flatMap(response -> {
                    Object dataObj = response.get("data");
                    List<Map<String, Object>> members;
                    if (dataObj instanceof List) {
                        members = (List<Map<String, Object>>) dataObj;
                    } else if (dataObj instanceof Map) {
                        Object inner = ((Map<String, Object>) dataObj).get("data");
                        members = (inner instanceof List) ? (List<Map<String, Object>>) inner : List.of();
                    } else {
                        members = List.of();
                    }

                    StringBuilder sb = new StringBuilder();
                    sb.append("👥 成员列表\n项目ID: ").append(projectId).append("\n");
                    if (members.isEmpty()) {
                        sb.append("暂无成员");
                    } else {
                        for (Map<String, Object> m : members) {
                            sb.append(getVal(m, "username", "")).append("\n");
                        }
                        sb.append("\n共 ").append(members.size()).append(" 人");
                    }
                    return sendMsg(token, chatId, sb.toString(), null);
                })
                .onErrorResume(e -> {
                    log.warn("{}⚠️ Failed to fetch members: {}", prefix, e.getMessage());
                    return sendMsg(token, chatId, "⚠️ 查询失败: " + e.getMessage(), null);
                });
    }

    private Mono<Void> fetchProjectDomains(String prefix, String token, Long chatId, Long messageId, Long projectId, String env, String tgUsername) {
        String cacheKey = "bot:devops:domains:" + projectId + ":" + env;
        WebClient webClient = getBuilder(getUserBaseUrl()).baseUrl(getUserBaseUrl())
                .defaultHeader("X-Tg-Username", tgUsername != null ? tgUsername : "bot").build();

        String uri = "/domain/list?projectId={projectId}&env={env}";
        if (env == null || env.isEmpty()) uri = "/domain/list?projectId={projectId}";

        return cacheOrFetch(cacheKey, Duration.ofSeconds(60),
                webClient.get().uri(uri, projectId, env).retrieve())
                .flatMap(response -> {
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

                    StringBuilder sb = new StringBuilder();
                    sb.append("🌐 域名列表\n项目ID: ").append(projectId).append("\n");
                    if (domains.isEmpty()) {
                        sb.append("暂无域名");
                    } else {
                        for (Map<String, Object> d : domains) {
                            String name = getVal(d, "domainName", getVal(d, "domain", "")).toString();
                            String env = getVal(d, "env", "").toString();
                            String type = getVal(d, "type", "").toString();
                            sb.append(name);
                            if (!env.isEmpty() || !type.isEmpty()) sb.append(" [").append(type).append("/").append(env).append("]");
                            sb.append("\n");
                        }
                        sb.append("\n共 ").append(domains.size()).append(" 个");
                    }
                    return sendMsg(token, chatId, sb.toString(), null);
                })
                .onErrorResume(e -> {
                    log.warn("{}⚠️ Failed to fetch domains: {}", prefix, e.getMessage());
                    return sendMsg(token, chatId, "⚠️ 查询失败: " + e.getMessage(), null);
                });
    }

    private Mono<Void> fetchProjectMiddlewares(String prefix, String token, Long chatId, Long messageId, Long projectId, String tgUsername) {
        String cacheKey = "bot:devops:middlewares:" + projectId;
        WebClient webClient = getBuilder(getUserBaseUrl()).baseUrl(getUserBaseUrl())
                .defaultHeader("X-Tg-Username", tgUsername != null ? tgUsername : "bot").build();

        return cacheOrFetch(cacheKey, Duration.ofSeconds(60),
                webClient.get().uri("/middleware/list?projectId={projectId}", projectId).retrieve())
                .flatMap(response -> {
                    Object dataObj = response.get("data");
                    List<Map<String, Object>> items;
                    if (dataObj instanceof List) {
                        items = (List<Map<String, Object>>) dataObj;
                    } else if (dataObj instanceof Map) {
                        Object inner = ((Map<String, Object>) dataObj).get("data");
                        items = (inner instanceof List) ? (List<Map<String, Object>>) inner : List.of();
                    } else {
                        items = List.of();
                    }

                    StringBuilder sb = new StringBuilder();
                    sb.append("🔧 中间件列表\n项目ID: ").append(projectId).append("\n");
                    if (items.isEmpty()) {
                        sb.append("暂无数据");
                    } else {
                        for (Map<String, Object> m : items) {
                            sb.append(getVal(m, "name", "")).append("\n");
                        }
                        sb.append("\n共 ").append(items.size()).append(" 个");
                    }
                    return sendMsg(token, chatId, sb.toString(), null);
                })
                .onErrorResume(e -> {
                    log.warn("{}⚠️ Failed to fetch middlewares: {}", prefix, e.getMessage());
                    return sendMsg(token, chatId, "⚠️ 查询失败: " + e.getMessage(), null);
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
                                token, null, chatId, msgId, 30, "DevOpsHandler", LogUtils.buildTraceContext()
                        ).subscribe();
                    } catch (Exception e) {
                        log.warn("⚠️ sendMsg: failed to parse msgId: {}", e.getMessage());
                    }
                    return Mono.empty();
                })
                .then();
    }

    private Object getVal(Map<String, Object> data, String key, Object defaultVal) {
        Object val = data.get(key);
        return (val != null) ? val : defaultVal;
    }

    @SuppressWarnings("unchecked")
    private Mono<Map<String, Object>> cacheOrFetch(String cacheKey, Duration ttl, WebClient.ResponseSpec responseSpec) {
        return redisTemplate.opsForValue().get(cacheKey)
                .flatMap(cached -> {
                    try {
                        return Mono.just((Map<String, Object>) objectMapper.readValue(cached, Map.class));
                    } catch (Exception e) {
                        return Mono.<Map<String, Object>>empty();
                    }
                })
                .switchIfEmpty(
                        responseSpec.bodyToMono(MAP_TYPE)
                                .flatMap(response -> {
                                    try {
                                        return redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(response), ttl).thenReturn(response);
                                    } catch (Exception e) {
                                        return Mono.just(response);
                                    }
                                })
                );
    }
}
