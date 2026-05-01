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

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(15)
public class GroupProjectQueryHandler implements CallbackActionHandler {

    private final BotGroupProjectRepository botGroupProjectRepository;
    private final BotClientService botClientService;
    private final WebClient.Builder webClientBuilder;

    private static final String PROJECT_INFO_ACTION = "PROJECT_INFO_ACTION";
    private static final String PROJECT_MEMBER_ACTION = "PROJECT_MEMBER_ACTION";
    private static final String PROJECT_DOMAIN_ACTION = "PROJECT_DOMAIN_ACTION";
    private static final String PROJECT_MIDDLEWARE_ACTION = "PROJECT_MIDDLEWARE_ACTION";
    private static final String MANAGE_SERVICE_URL = "http://manage:8083";

    @Override
    public boolean supports(String callbackData) {
        return callbackData.equals(PROJECT_INFO_ACTION)
                || callbackData.equals(PROJECT_MEMBER_ACTION)
                || callbackData.equals(PROJECT_DOMAIN_ACTION)
                || callbackData.equals(PROJECT_MIDDLEWARE_ACTION);
    }

    @Override
    public int getOrder() {
        return 15;
    }

    @Override
    public Mono<Void> handle(BotConfigEntity botEntity, BotUpdateDto botUpdate) {
        HandlerContext ctx = new HandlerContext(botEntity, botUpdate);
        String token = ctx.token();
        Long chatId = ctx.chatId();
        Long messageId = ctx.messageId();
        String botName = ctx.botName();
        String callbackData = botUpdate.callbackQuery().data();

        return Mono.deferContextual(contextView -> {
            String traceLogPrefix = LogUtils.prepareMdcAndGetPrefix(contextView);

            return botGroupProjectRepository.findByBotNameAndChatId(botName, chatId)
                    .flatMap(binding -> {
                        if (binding.getProjectId() == null) {
                            return replyNoBinding(token, chatId, messageId, traceLogPrefix);
                        }

                        WebClient webClient = webClientBuilder.baseUrl(MANAGE_SERVICE_URL).build();

                        return switch (callbackData) {
                            case PROJECT_INFO_ACTION -> fetchAndReplyProjectInfo(webClient, binding, token, chatId, messageId, traceLogPrefix);
                            case PROJECT_MEMBER_ACTION -> replyNotImplemented(token, chatId, messageId, "成员列表", traceLogPrefix);
                            case PROJECT_DOMAIN_ACTION -> replyNotImplemented(token, chatId, messageId, "域名列表", traceLogPrefix);
                            case PROJECT_MIDDLEWARE_ACTION -> replyNotImplemented(token, chatId, messageId, "中间件", traceLogPrefix);
                            default -> Mono.empty();
                        };
                    })
                    .switchIfEmpty(Mono.defer(() -> replyNoBinding(token, chatId, messageId, traceLogPrefix)))
                    .onErrorResume(e -> {
                        log.error("{}❌ GroupProjectQueryHandler error: {}", traceLogPrefix, e.getMessage(), e);
                        return botClientService.sendMessage(token, chatId, "⚠️ 查询失败，请稍后再试。", null)
                                .then();
                    })
                    .then();
        });
    }

    private Mono<Void> fetchAndReplyProjectInfo(WebClient webClient, BotGroupProjectEntity binding,
                                                  String token, Long chatId, Long messageId, String traceLogPrefix) {
        return webClient.get()
                .uri("/project/{id}", binding.getProjectId())
                .retrieve()
                .bodyToMono(Map.class)
                .flatMap(project -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = project.containsKey("data") ? (Map<String, Object>) project.get("data") : project;

                    StringBuilder sb = new StringBuilder();
                    sb.append("📋 **项目信息**\n\n");
                    sb.append("项目名称：").append(data.getOrDefault("projectName", binding.getProjectName())).append("\n");
                    sb.append("项目描述：").append(data.getOrDefault("description", "暂无")).append("\n");
                    sb.append("技术栈：").append(data.getOrDefault("techStack", "暂无")).append("\n");
                    sb.append("状态：").append(data.getOrDefault("status", "未知")).append("\n");
                    sb.append("创建时间：").append(data.getOrDefault("createdAt", "暂无")).append("\n");

                    String text = sb.toString();
                    return botClientService.editMessageText(token, chatId, messageId, text, null)
                            .onErrorResume(e -> botClientService.sendMessage(token, chatId, text, null))
                            .then();
                })
                .onErrorResume(e -> {
                    log.warn("{}⚠️ Failed to fetch project info from manage service: {}", traceLogPrefix, e.getMessage());
                    String text = String.format("📋 **项目信息（本地缓存）**\n\n项目名称：%s\n项目ID：%d\n\n⚠️ 无法从管理服务获取详细信息", binding.getProjectName(), binding.getProjectId());
                    return botClientService.editMessageText(token, chatId, messageId, text, null)
                            .onErrorResume(ex -> botClientService.sendMessage(token, chatId, text, null))
                            .then();
                });
    }

    private Mono<Void> replyNoBinding(String token, Long chatId, Long messageId, String traceLogPrefix) {
        String text = "该群组未绑定项目，请联系管理员配置。";
        return botClientService.editMessageText(token, chatId, messageId, text, null)
                .onErrorResume(e -> botClientService.sendMessage(token, chatId, text, null))
                .then();
    }

    private Mono<Void> replyNotImplemented(String token, Long chatId, Long messageId, String feature, String traceLogPrefix) {
        String text = String.format("⚠️ %s功能开发中，敬请期待...", feature);
        return botClientService.editMessageText(token, chatId, messageId, text, null)
                .onErrorResume(e -> botClientService.sendMessage(token, chatId, text, null))
                .then();
    }
}
