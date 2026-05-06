package com.backend.bot.controller;

import com.backend.bot.entity.BotGroupEntity;
import com.backend.bot.entity.BotGroupTopicEntity;
import com.backend.bot.repository.BotGroupRepository;
import com.backend.bot.repository.BotGroupTopicRepository;
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

    // ========== Group CRUD ==========

    @GetMapping("/{botName}")
    public Mono<ResponseEntity<Map<String, Object>>> list(@PathVariable String botName) {
        return botGroupRepository.findByBotName(botName)
                .collectList()
                .flatMap(groups -> {
                    if (groups.isEmpty()) {
                        return Mono.just(HttpResponseUtils.ok(Map.of("groups", List.of(), "total", 0)));
                    }
                    // Fetch topics for each group
                    return Flux.fromIterable(groups)
                            .flatMap(group -> botGroupTopicRepository.findByBotNameAndChatId(group.getBotName(), group.getChatId())
                                    .collectList()
                                    .map(topics -> Map.of(
                                            "id", group.getId(),
                                            "botName", group.getBotName(),
                                            "chatId", group.getChatId(),
                                            "chatTitle", group.getChatTitle(),
                                            "chatType", group.getChatType(),
                                            "status", group.getStatus(),
                                            "topicCount", topics.size(),
                                            "topics", topics.stream().map(t -> Map.of(
                                                    "id", t.getId(),
                                                    "threadId", t.getThreadId(),
                                                    "topicName", t.getTopicName()
                                            )).toList()
                                    )))
                            .collectList()
                            .map(enriched -> HttpResponseUtils.ok(Map.of("groups", enriched, "total", enriched.size())));
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
}
