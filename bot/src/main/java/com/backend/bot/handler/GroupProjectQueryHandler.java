package com.backend.bot.handler;

import com.backend.bot.context.HandlerContext;
import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.entity.BotConfigEntity;
import com.backend.bot.entity.BotGroupProjectEntity;
import com.backend.bot.repository.BotGroupProjectRepository;
import com.backend.bot.service.BotClientService;
import com.backend.bot.util.LogUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.core.ParameterizedTypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(5)
public class GroupProjectQueryHandler implements CallbackActionHandler {

    private final BotGroupProjectRepository botGroupProjectRepository;
    private final BotClientService botClientService;
    private final WebClient.Builder webClientBuilder;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final Duration GP_CACHE_TTL = Duration.ofSeconds(300);
    private static final Duration USER_CACHE_TTL = Duration.ofSeconds(60);
    private static final TypeReference<Map<String, Object>> JACKSON_MAP_TYPE = new TypeReference<>() {};

    private static final String PROJECT_INFO_ACTION = "PROJECT_INFO_ACTION";
    private static final String PROJECT_MEMBER_ACTION = "PROJECT_MEMBER_ACTION";
    private static final String PROJECT_DOMAIN_PROD_ACTION = "PROJECT_DOMAIN_PROD_ACTION";
    private static final String PROJECT_DOMAIN_UAT_ACTION = "PROJECT_DOMAIN_UAT_ACTION";
    private static final String PROJECT_DOMAIN_TEST_ACTION = "PROJECT_DOMAIN_TEST_ACTION";
    private static final String PROJECT_DOMAIN_DEV_ACTION = "PROJECT_DOMAIN_DEV_ACTION";
    private static final String PROJECT_MIDDLEWARE_ACTION = "PROJECT_MIDDLEWARE_ACTION";
    private static final String USER_SERVICE_URL = "http://192.168.86.9:8084";

    @Override
    public boolean supports(String callbackData) {
        return callbackData.equals("callback_data_" + PROJECT_INFO_ACTION)
                || callbackData.equals("callback_data_" + PROJECT_MEMBER_ACTION)
                || callbackData.equals("callback_data_" + PROJECT_DOMAIN_PROD_ACTION)
                || callbackData.equals("callback_data_" + PROJECT_DOMAIN_UAT_ACTION)
                || callbackData.equals("callback_data_" + PROJECT_DOMAIN_TEST_ACTION)
                || callbackData.equals("callback_data_" + PROJECT_DOMAIN_DEV_ACTION)
                || callbackData.equals("callback_data_" + PROJECT_MIDDLEWARE_ACTION);
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
        Long userId = ctx.userId();
        String botName = ctx.botName();
        String callbackData = botUpdate.callbackQuery().data();
        String action = callbackData.replace("callback_data_", "");
        String tgUsername = botUpdate.callbackQuery() != null && botUpdate.callbackQuery().from() != null
                ? botUpdate.callbackQuery().from().username() : null;

        return Mono.deferContextual(contextView -> {
            String traceLogPrefix = LogUtils.prepareMdcAndGetPrefix(contextView);

            log.info("{}🔍 GroupProjectQueryHandler: action={}, chatId={}, botName={}, tgUsername={}", traceLogPrefix, action, chatId, botName, tgUsername);

            String gpCacheKey = "bot:groupProject:" + botName + ":" + chatId;
            return redisTemplate.opsForValue().get(gpCacheKey)
                    .flatMap(cached -> {
                        try {
                            return Mono.just(objectMapper.readValue(cached, BotGroupProjectEntity.class));
                        } catch (Exception e) {
                            log.warn("{}⚠️ Failed to deserialize cached groupProject, fetching from DB", traceLogPrefix);
                            return Mono.empty();
                        }
                    })
                    .switchIfEmpty(botGroupProjectRepository.findByBotNameAndChatId(botName, chatId)
                            .flatMap(entity -> {
                                try {
                                    String json = objectMapper.writeValueAsString(entity);
                                    return redisTemplate.opsForValue().set(gpCacheKey, json, GP_CACHE_TTL).thenReturn(entity);
                                } catch (Exception e) {
                                    return Mono.just(entity);
                                }
                            }))
                    .doOnNext(b -> log.info("{}🔍 Found binding: projectId={}, projectName={}", traceLogPrefix, b.getProjectId(), b.getProjectName()))
                    .switchIfEmpty(Mono.defer(() -> {
                        log.warn("{}⚠️ No group-project binding found for bot={}, chatId={}", traceLogPrefix, botName, chatId);
                        return Mono.empty();
                    }))
                    .flatMap(binding -> {
                        if (binding.getProjectId() == null) {
                            return replyNoBinding(token, chatId, messageId);
                        }

                        WebClient webClient = webClientBuilder.baseUrl(USER_SERVICE_URL)
                                .defaultHeader("X-Tg-Username", tgUsername != null ? tgUsername : "bot")
                                .build();

                        // 先查 TG 用户的 project role
                        return resolveUserRole(webClient, binding.getProjectId(), tgUsername, traceLogPrefix)
                                .flatMap(role -> {
                                    log.info("{}🔍 Resolved role: {} for tgUsername={}", traceLogPrefix, role, tgUsername);
                                    if ("None".equals(role)) {
                                        return replyText(token, chatId, messageId, "⚠️ 您不是该项目成员，无权限查看。");
                                    }
                                    return switch (action) {
                                        case PROJECT_INFO_ACTION -> fetchProjectInfo(webClient, binding, token, chatId, messageId, traceLogPrefix);
                                        case PROJECT_MEMBER_ACTION -> fetchList(webClient, binding, "/projectMember?projectId=" + binding.getProjectId(), token, chatId, messageId, "👥 成员列表", null, traceLogPrefix);
                                        case PROJECT_DOMAIN_PROD_ACTION -> fetchList(webClient, binding, "/domain/list?projectId=" + binding.getProjectId() + "&env=PROD", token, chatId, messageId, "🌐 生产域名列表", role, traceLogPrefix);
                                        case PROJECT_DOMAIN_UAT_ACTION -> fetchList(webClient, binding, "/domain/list?projectId=" + binding.getProjectId() + "&env=UAT", token, chatId, messageId, "🌐 UAT域名列表", role, traceLogPrefix);
                                        case PROJECT_DOMAIN_TEST_ACTION -> fetchList(webClient, binding, "/domain/list?projectId=" + binding.getProjectId() + "&env=TEST", token, chatId, messageId, "🌐 TEST域名列表", role, traceLogPrefix);
                                        case PROJECT_DOMAIN_DEV_ACTION -> fetchList(webClient, binding, "/domain/list?projectId=" + binding.getProjectId() + "&env=DEV", token, chatId, messageId, "🌐 DEV域名列表", role, traceLogPrefix);
                                        case PROJECT_MIDDLEWARE_ACTION -> fetchList(webClient, binding, "/middleware/list?projectId=" + binding.getProjectId(), token, chatId, messageId, "🔧 中间件列表", role, traceLogPrefix);
                                        default -> Mono.empty();
                                    };
                                });
                    })
                    .onErrorResume(e -> {
                        log.error("{}❌ GroupProjectQueryHandler error: {}", traceLogPrefix, e.getMessage(), e);
                        return botClientService.sendMessage(token, chatId, "⚠️ 查询失败，请稍后再试。", null).then();
                    })
                    .then();
        });
    }

    /**
     * 查询 TG 用户在项目中的角色
     * 通过 projectMember 接口获取成员列表，匹配 username
     */
    private Mono<String> resolveUserRole(WebClient webClient, Long projectId, String tgUsername, String traceLogPrefix) {
        if (tgUsername == null || tgUsername.isBlank()) {
             return Mono.just("None");
        }
        String membersCacheKey = "bot:projectMembers:" + projectId;
        return cacheOrFetch(membersCacheKey, USER_CACHE_TTL,
                webClient.get().uri("/projectMember?projectId={projectId}", projectId).retrieve())
                .map(response -> {
                    log.debug("{}🔍 Member response: {}", traceLogPrefix, response);
                    Object code = response.get("code");
                    if (code != null && !"200".equals(String.valueOf(code)) && !"201".equals(String.valueOf(code))) {
                        return "None";
                    }
                    // response.data 可能是 List（直接返回数组）或 Map（再包一层）
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
                    log.debug("{}🔍 Members count: {}, tgUsername: {}", traceLogPrefix, members.size(), tgUsername);
                    return members.stream()
                            .filter(m -> tgUsername.equalsIgnoreCase(String.valueOf(getVal(m, "tgUsername", ""))))
                            .map(m -> String.valueOf(getVal(m, "projectRole", "Member")))
                            .findFirst()
                            .orElse("None");
                })
                .onErrorReturn("None");
    }

    private Mono<Void> fetchProjectInfo(WebClient webClient, BotGroupProjectEntity binding,
                                           String token, Long chatId, Long messageId, String traceLogPrefix) {
        log.info("{}🔍 Fetching project info: projectId={}", traceLogPrefix, binding.getProjectId());
        String projectCacheKey = "bot:project:" + binding.getProjectId();
        return cacheOrFetch(projectCacheKey, USER_CACHE_TTL,
                webClient.get().uri("/project/{id}", binding.getProjectId()).retrieve())
                .doOnNext(project -> log.info("{}🔍 Project response: {}", traceLogPrefix, project))
                .flatMap(project -> {
                    Object code = project.get("code");
                    if (code != null && !"200".equals(String.valueOf(code)) && !"201".equals(String.valueOf(code))) {
                        return replyText(token, chatId, messageId, "⚠️ 查询失败：" + project.getOrDefault("message", "未知错误"));
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = project.containsKey("data") ? (Map<String, Object>) project.get("data") : project;

                    if (data == null) {
                        return replyText(token, chatId, messageId,
                                String.format("📋 *项目信息（本地）*\n\n项目名称：%s\n项目ID：%d\n\n⚠️ 暂无详细信息", binding.getProjectName(), binding.getProjectId()));
                    }

                    StringBuilder sb = new StringBuilder();
                    sb.append("📋 *项目信息*\n\n");
                    sb.append("项目名称：").append(getVal(data, "name", binding.getProjectName())).append("\n");
                    sb.append("描述：").append(getVal(data, "description", "暂无")).append("\n");
                    sb.append("技术栈：").append(getVal(data, "techStack", "暂无")).append("\n");
                    sb.append("状态：").append(getVal(data, "status", "未知")).append("\n");
                    sb.append("进度：").append(getVal(data, "progress", 0)).append("%\n");
                    sb.append("创建时间：").append(getVal(data, "createdAt", "暂无")).append("\n");

                    return replyText(token, chatId, messageId, sb.toString());
                })
                .onErrorResume(e -> {
                    log.warn("{}⚠️ Failed to fetch project info: {}", traceLogPrefix, e.getMessage());
                    return replyText(token, chatId, messageId,
                            String.format("📋 *项目信息（本地）*\n\n项目名称：%s\n项目ID：%d\n\n⚠️ 暂无详细信息", binding.getProjectName(), binding.getProjectId()));
                });
    }

    private Mono<Void> fetchList(WebClient webClient, BotGroupProjectEntity binding, String uri,
                                  String token, Long chatId, Long messageId, String title, String role, String traceLogPrefix) {
        log.info("{}🔍 Fetching list: title={}, role={}", traceLogPrefix, title, role);
        // Determine cache key based on URI
        String cacheKey;
        if (uri.contains("/projectMember")) {
            cacheKey = "bot:projectMembers:" + binding.getProjectId();
        } else if (uri.contains("/domain/")) {
            cacheKey = "bot:domains:" + binding.getProjectId() + ":" + role;
        } else {
            cacheKey = "bot:middlewares:" + binding.getProjectId() + ":" + role;
        }

        return cacheOrFetch(cacheKey, USER_CACHE_TTL,
                webClient.get().uri(uri).retrieve())
                .flatMap(response -> {
                    // 检查响应 code
                    Object code = response.get("code");
                    if (code != null && !"200".equals(String.valueOf(code)) && !"201".equals(String.valueOf(code))) {
                        return replyText(token, chatId, messageId, "⚠️ 查询失败：" + response.getOrDefault("message", "未知错误"));
                    }
                    // 响应结构: {code:200, data: [...]} 或 {code:200, data: {data: [...]}}
                    Object dataObj = response.get("data");
                    List<Map<String, Object>> items;
                    if (dataObj instanceof List) {
                        items = (List<Map<String, Object>>) dataObj;
                    } else if (dataObj instanceof Map) {
                        Map<String, Object> inner = (Map<String, Object>) dataObj;
                        Object innerData = inner.get("data");
                        items = (innerData instanceof List) ? (List<Map<String, Object>>) innerData : List.of();
                    } else {
                        items = List.of();
                    }

                    // 跟前端一样的角色过滤
                    if ("Member".equals(role) && items != null && !items.isEmpty()) {
                        items = applyMemberFilter(items, title);
                    }

                    StringBuilder sb = new StringBuilder();
                    sb.append(title).append("\n");
                    sb.append("项目：").append(binding.getProjectName()).append("\n\n");

                    // 域名列表按环境分组（prod > uat > test > dev）
                    if (items != null && !items.isEmpty() && title.contains("域名")) {
                        String[] envOrder = {"prod", "uat", "test", "dev"};
                        Map<String, List<Map<String, Object>>> grouped = items.stream()
                                .collect(java.util.stream.Collectors.groupingBy(
                                        d -> String.valueOf(getVal(d, "env", "其他")),
                                        java.util.stream.Collectors.toList()
                                ));
                        for (String env : envOrder) {
                            if (!grouped.containsKey(env)) continue;
                            sb.append("环境：").append(env).append("\n");
                            int idx = 1;
                            for (Map<String, Object> m : grouped.get(env)) {
                                String domainName = getVal(m, "domainName", getVal(m, "domain", "")).toString();
                                String type = getVal(m, "type", "").toString();
                                String remark = getVal(m, "remark", "").toString();
                                sb.append(idx++).append(". ").append(domainName);
                                sb.append(" ").append(type).append("/").append(remark.isEmpty() ? "无" : remark);
                                sb.append("\n");
                            }
                            sb.append("\n");
                        }
                        // 输出未在排序中的其他环境
                        for (Map.Entry<String, List<Map<String, Object>>> entry : grouped.entrySet()) {
                            if (java.util.Arrays.asList(envOrder).contains(entry.getKey())) continue;
                            sb.append("环境：").append(entry.getKey()).append("\n");
                            int idx2 = 1;
                            for (Map<String, Object> m : entry.getValue()) {
                                String domainName = getVal(m, "domainName", getVal(m, "domain", "")).toString();
                                String type = getVal(m, "type", "").toString();
                                String remark = getVal(m, "remark", "").toString();
                                sb.append(idx2++).append(". ").append(domainName);
                                sb.append(" ").append(type).append("/").append(remark.isEmpty() ? "无" : remark);
                                sb.append("\n");
                            }
                            sb.append("\n");
                        }
                        sb.append("共 ").append(items.size()).append(" 条");
                    } else if (items == null || items.isEmpty()) {
                        sb.append("暂无数据");
                    } else {
                        int idx = 1;
                        for (Map<String, Object> m : items) {
                            sb.append(idx++).append(". ");
                            if (m.containsKey("domainName") || m.containsKey("domain")) {
                                String domainName = getVal(m, "domainName", getVal(m, "domain", "")).toString();
                                sb.append(domainName);
                                String env = getVal(m, "env", "").toString();
                                String type = getVal(m, "type", "").toString();
                                if (!env.isEmpty() || !type.isEmpty()) {
                                    sb.append(" [").append(type).append("/").append(env).append("]");
                                }
                            } else if (m.containsKey("name")) {
                                sb.append(getVal(m, "name", ""));
                                if (m.containsKey("type")) sb.append(" [").append(getVal(m, "type", "")).append("]");
                                if (m.containsKey("env")) sb.append(" (").append(getVal(m, "env", "")).append(")");
                            } else if (m.containsKey("username")) {
                                sb.append(getVal(m, "username", ""));
                                if (m.containsKey("fullName")) sb.append(" (").append(getVal(m, "fullName", "")).append(")");
                                if (m.containsKey("projectRole")) sb.append(" [").append(getVal(m, "projectRole", "")).append("]");
                            } else {
                                sb.append(m.toString());
                            }
                            sb.append("\n");
                        }
                        sb.append("\n共 ").append(idx - 1).append(" 条");
                    }

                    return replyText(token, chatId, messageId, sb.toString());
                })
                .onErrorResume(e -> {
                    log.warn("{}⚠️ Failed to fetch {}: {}", traceLogPrefix, title, e.getMessage());
                    return replyText(token, chatId, messageId, "⚠️ 查询失败：" + e.getMessage());
                });
    }

    /**
     * Member 角色过滤：跟前端逻辑一致
     * 域名：prod 环境只显示 web 类型
     * 中间件：隐藏 prod 环境
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> applyMemberFilter(List<Map<String, Object>> items, String title) {
        if (title.contains("域名")) {
            // Member: prod 环境只显示 web 类型
            return items.stream()
                    .filter(d -> !"prod".equals(String.valueOf(getVal(d, "env", ""))) || "web".equals(String.valueOf(getVal(d, "type", ""))))
                    .toList();
        } else if (title.contains("中间件")) {
            // Member: 隐藏 prod 环境
            return items.stream()
                    .filter(m -> !"prod".equals(String.valueOf(getVal(m, "env", ""))))
                    .toList();
        }
        return items;
    }

    private Mono<Void> replyText(String token, Long chatId, Long messageId, String text) {
        return botClientService.editMessageText(token, chatId, messageId, text, null)
                .onErrorResume(e -> botClientService.sendMessage(token, chatId, text, null))
                .then();
    }

    private Mono<Void> replyNoBinding(String token, Long chatId, Long messageId) {
        return replyText(token, chatId, messageId, "该群组未绑定项目，请联系管理员配置。");
    }

    private Object getVal(Map<String, Object> data, String key, Object defaultVal) {
        Object val = data.get(key);
        return (val != null) ? val : defaultVal;
    }

    private Mono<Map<String, Object>> cacheOrFetch(String cacheKey, Duration ttl,
                                                      WebClient.ResponseSpec responseSpec) {
        return redisTemplate.opsForValue().get(cacheKey)
                .flatMap(cached -> {
                    try {
                        return Mono.just(objectMapper.readValue(cached, JACKSON_MAP_TYPE));
                    } catch (Exception e) {
                        return Mono.<Map<String, Object>>empty();
                    }
                })
                .switchIfEmpty(
                        responseSpec.bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
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
