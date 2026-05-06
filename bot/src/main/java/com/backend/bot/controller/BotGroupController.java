package com.backend.bot.controller;

import com.backend.bot.entity.BotGroupEntity;
import com.backend.bot.entity.BotGroupTopicEntity;
import com.backend.bot.repository.BotGroupRepository;
import com.backend.bot.repository.BotGroupTopicRepository;
import com.backend.bot.service.BotClientService;
import com.backend.bot.service.BotCoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import network.HttpResponseUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/group")
public class BotGroupController {

    private final BotGroupRepository botGroupRepository;
    private final BotGroupTopicRepository botGroupTopicRepository;
    private final BotClientService botClientService;
    private final BotCoreService botCoreService;

    // ========== Group CRUD ==========

    @GetMapping("/{botName}")
    public Mono<ResponseEntity<Map<String, Object>>> list(@PathVariable String botName) {
        return botGroupRepository.findByBotName(botName)
                .collectList()
                .flatMap(groups -> {
                    if (groups.isEmpty()) {
                        return Mono.just(HttpResponseUtils.ok(Map.of("groups", List.of(), "total", 0)));
                    }
                    return Flux.fromIterable(groups)
                            .flatMap(group -> botGroupTopicRepository.findByBotNameAndChatId(group.getBotName(), group.getChatId())
                                    .collectList()
                                    .map(topics -> {
                                        Map<String, Object> m = new java.util.HashMap<>();
                                        m.put("id", group.getId());
                                        m.put("botName", group.getBotName() != null ? group.getBotName() : "");
                                        m.put("botConfigId", group.getBotConfigId() != null ? group.getBotConfigId() : 0);
                                        m.put("chatId", group.getChatId());
                                        m.put("chatTitle", group.getChatTitle() != null ? group.getChatTitle() : "");
                                        m.put("chatType", group.getChatType() != null ? group.getChatType() : "");
                                        m.put("projectId", group.getProjectId() != null ? group.getProjectId() : 0);
                                        m.put("projectName", group.getProjectName() != null ? group.getProjectName() : "");
                                        m.put("status", group.getStatus() != null ? group.getStatus() : 0);
                                        m.put("topicCount", topics.size());
                                        m.put("topics", topics.stream().map(t -> {
                                            Map<String, Object> tm = new java.util.HashMap<>();
                                            tm.put("id", t.getId());
                                            tm.put("threadId", t.getThreadId() != null ? t.getThreadId() : 0);
                                            tm.put("topicName", t.getTopicName() != null ? t.getTopicName() : "");
                                            return tm;
                                        }).toList());
                                        return m;
                                    }))
                            .collectList()
                            .map(enriched -> {
                                Map<String, Object> result = new java.util.HashMap<>();
                                result.put("groups", enriched);
                                result.put("total", enriched.size());
                                return HttpResponseUtils.ok(result);
                            });
                });
    }

    @PostMapping
    public Mono<ResponseEntity<Map<String, Object>>> create(@RequestBody BotGroupEntity entity) {
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        if (entity.getStatus() == null) entity.setStatus(1);
        return botGroupRepository.save(entity)
                .map(saved -> HttpResponseUtils.ok(Map.of("group", saved)));
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> update(@PathVariable Long id, @RequestBody BotGroupEntity entity) {
        return botGroupRepository.findById(id)
                .flatMap(existing -> {
                    if (entity.getChatTitle() != null) existing.setChatTitle(entity.getChatTitle());
                    if (entity.getChatType() != null) existing.setChatType(entity.getChatType());
                    if (entity.getProjectId() != null) existing.setProjectId(entity.getProjectId());
                    if (entity.getProjectName() != null) existing.setProjectName(entity.getProjectName());
                    if (entity.getBotConfigId() != null) existing.setBotConfigId(entity.getBotConfigId());
                    if (entity.getStatus() != null) existing.setStatus(entity.getStatus());
                    existing.setUpdatedAt(LocalDateTime.now());
                    return botGroupRepository.save(existing);
                })
                .map(saved -> HttpResponseUtils.ok(Map.of("group", saved)))
                .defaultIfEmpty(HttpResponseUtils.notFound("Group not found: " + id));
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> delete(@PathVariable Long id) {
        return botGroupRepository.findById(id)
                .flatMap(entity -> botGroupTopicRepository.deleteByBotNameAndChatId(entity.getBotName(), entity.getChatId())
                        .then(botGroupRepository.delete(entity))
                        .thenReturn(HttpResponseUtils.ok(Map.of("deleted", id))))
                .defaultIfEmpty(HttpResponseUtils.notFound("Group not found: " + id));
    }

    // ========== Topic CRUD ==========

    @PostMapping("/topic")
    public Mono<ResponseEntity<Map<String, Object>>> addTopic(@RequestBody BotGroupTopicEntity entity) {
        entity.setCreatedAt(LocalDateTime.now());
        if (entity.getThreadId() == null && entity.getTopicName() != null) {
            return botCoreService.findByBotName(entity.getBotName())
                    .flatMap(botConfig -> botClientService.createForumTopic(botConfig.getBotToken(), entity.getChatId(), entity.getTopicName())
                            .onErrorResume(e -> {
                                log.warn("createForumTopic failed for chatId={}: {}", entity.getChatId(), e.getMessage());
                                return Mono.error(new RuntimeException("Failed to create topic: group may not have Topics feature enabled. Error: " + e.getMessage()));
                            })
                            .flatMap(threadId -> {
                                entity.setThreadId(threadId);
                                String welcomeMsg = "📋 " + entity.getTopicName();
                                return botGroupTopicRepository.save(entity)
                                        .flatMap(saved -> botClientService.sendMessageToThread(botConfig.getBotToken(), entity.getChatId(), threadId, welcomeMsg)
                                                .onErrorResume(e -> { log.warn("Welcome message failed: {}", e.getMessage()); return Mono.empty(); })
                                                .thenReturn(saved))
                                        .map(saved -> HttpResponseUtils.ok(Map.of("topic", saved)));
                            }))
                    .switchIfEmpty(Mono.defer(() -> botGroupTopicRepository.save(entity).map(saved -> HttpResponseUtils.ok(Map.of("topic", saved)))));
        }
        return botGroupTopicRepository.save(entity)
                .map(saved -> HttpResponseUtils.ok(Map.of("topic", saved)));
    }

    @PutMapping("/topic/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> updateTopic(@PathVariable Long id, @RequestBody BotGroupTopicEntity entity) {
        return botGroupTopicRepository.findById(id)
                .flatMap(existing -> {
                    if (entity.getTopicName() != null) existing.setTopicName(entity.getTopicName());
                    if (entity.getThreadId() != null) existing.setThreadId(entity.getThreadId());
                    return botGroupTopicRepository.save(existing);
                })
                .map(saved -> HttpResponseUtils.ok(Map.of("topic", saved)))
                .defaultIfEmpty(HttpResponseUtils.notFound("Topic not found: " + id));
    }

    @DeleteMapping("/topic/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> deleteTopic(@PathVariable Long id) {
        return botGroupTopicRepository.findById(id)
                .flatMap(entity -> botGroupTopicRepository.delete(entity)
                        .thenReturn(HttpResponseUtils.ok(Map.of("deleted", id))))
                .defaultIfEmpty(HttpResponseUtils.notFound("Topic not found: " + id));
    }

    @DeleteMapping("/topics/{groupId}")
    public Mono<ResponseEntity<Map<String, Object>>> clearTopics(@PathVariable Long groupId) {
        return botGroupRepository.findById(groupId)
                .flatMap(group -> botGroupTopicRepository.deleteByBotNameAndChatId(group.getBotName(), group.getChatId())
                        .thenReturn(HttpResponseUtils.ok(Map.of("cleared", groupId))))
                .defaultIfEmpty(HttpResponseUtils.notFound("Group not found: " + groupId));
    }
}
