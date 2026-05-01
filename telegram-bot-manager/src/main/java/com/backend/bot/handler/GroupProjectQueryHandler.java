package com.backend.bot.handler;

import com.backend.bot.context.HandlerContext;
import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.entity.BotConfigEntity;
import com.backend.bot.entity.BotGroupProjectEntity;
import com.backend.bot.repository.BotGroupProjectRepository;
import com.backend.bot.service.BotClientService;
import com.backend.bot.util.LogUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

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

    private static final String PROJECT_INFO_ACTION = "PROJECT_INFO_ACTION";
    private static final String PROJECT_MEMBER_ACTION = "PROJECT_MEMBER_ACTION";
    private static final String PROJECT_DOMAIN_ACTION = "PROJECT_DOMAIN_ACTION";
    private static final String PROJECT_MIDDLEWARE_ACTION = "PROJECT_MIDDLEWARE_ACTION";
    private static final String USER_SERVICE_URL = "http://192.168.86.9:8084";

    @Override
    public boolean supports(String callbackData) {
        return callbackData.equals("callback_data_" + PROJECT_INFO_ACTION)
                || callbackData.equals("callback_data_" + PROJECT_MEMBER_ACTION)
                || callbackData.equals("callback_data_" + PROJECT_DOMAIN_ACTION)
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
        String botName = ctx.botName();
        String callbackData = botUpdate.callbackQuery().data();
        String action = callbackData.replace("callback_data_", "");
        String tgUsername = botUpdate.callbackQuery() != null && botUpdate.callbackQuery().from() != null
                ? botUpdate.callbackQuery().from().username() : null;

        return Mono.deferContextual(contextView -> {
            String traceLogPrefix = LogUtils.prepareMdcAndGetPrefix(contextView);

            log.info("{}🔍 GroupProjectQueryHandler: action={}, chatId={}, botName={}, tgUsername={}", traceLogPrefix, action, chatId, botName, tgUsername);

            return botGroupProjectRepository.findByBotNameAndChatId(botName, chatId)
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
                                        case PROJECT_DOMAIN_ACTION -> fetchList(webClient, binding, "/domain/list?projectId=" + binding.getProjectId(), token, chatId, messageId, "🌐 域名列表", role, traceLogPrefix);
                                        case PROJECT_MIDDLEWARE_ACTION -> fetchList(webClient, binding, "/middleware/list?projectId=" + binding.getProjectId(), token, chatId, messageId, "🔧 中间件列表", role, traceLogPrefix);
                                        default -> Mono.empty();
                                    };
                                });
                    })
                    .switchIfEmpty(Mono.defer(() -> replyNoBinding(token, chatId, messageId)))
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
        return webClient.get()
                .uri("/projectMember?projectId={projectId}", projectId)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> res = response.containsKey("data") ? (Map<String, Object>) response.get("data") : response;
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> members = res.containsKey("data") ? (List<Map<String, Object>>) res.get("data") : List.of();
                    return members.stream()
                            .filter(m -> tgUsername.equalsIgnoreCase(String.valueOf(m.getOrDefault("tgUsername", ""))))
                            .map(m -> String.valueOf(m.getOrDefault("projectRole", "Member")))
                            .findFirst()
                            .orElse("Member");
                })
                .onErrorReturn("Member"); // 查询失败按 Member 处理
    }

    private Mono<Void> fetchProjectInfo(WebClient webClient, BotGroupProjectEntity binding,
                                           String token, Long chatId, Long messageId, String traceLogPrefix) {
        log.info("{}🔍 Fetching project info: projectId={}", traceLogPrefix, binding.getProjectId());
        return webClient.get()
                .uri("/project/{id}", binding.getProjectId())
                .retrieve()
                .bodyToMono(Map.class)
                .doOnNext(project -> log.info("{}🔍 Project response: {}", traceLogPrefix, project))
                .flatMap(project -> {
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
        return webClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(Map.class)
                .flatMap(response -> {
                    // 检查响应 code
                    Object code = response.get("code");
                    if (code != null && !"200".equals(String.valueOf(code)) && !"201".equals(String.valueOf(code))) {
                        return replyText(token, chatId, messageId, "⚠️ 查询失败：" + response.getOrDefault("message", "未知错误"));
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> res = response.containsKey("data") ? (Map<String, Object>) response.get("data") : response;
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> items = res.containsKey("data") ? (List<Map<String, Object>>) res.get("data") : (res instanceof List ? (List<Map<String, Object>>) res : List.of());

                    // 跟前端一样的角色过滤
                    if ("Member".equals(role) && items != null && !items.isEmpty()) {
                        items = applyMemberFilter(items, title);
                    }

                    StringBuilder sb = new StringBuilder();
                    sb.append(title).append("\n");
                    sb.append("项目：").append(binding.getProjectName()).append("\n\n");

                    if (items == null || items.isEmpty()) {
                        sb.append("暂无数据");
                    } else {
                        int idx = 1;
                        for (Map<String, Object> m : items) {
                            sb.append(idx++).append(". ");
                            if (m.containsKey("domainName")) {
                                sb.append(getVal(m, "domainName", ""));
                                if (m.containsKey("ip")) sb.append(" (").append(getVal(m, "ip", "")).append(")");
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
}
