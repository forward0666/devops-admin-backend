package com.backend.bot.controller;

import com.backend.bot.entity.BotGroupEntity;
import com.backend.bot.repository.BotGroupRepository;
import com.backend.bot.vo.BotGroupProjectVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import network.HttpResponseUtils;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/groupProject")
public class BotGroupProjectController {

    private final BotGroupRepository botGroupRepository;
    private final ReactiveStringRedisTemplate redisTemplate;

    @GetMapping("/bot/{botName}")
    public Mono<ResponseEntity<Map<String, Object>>> getByBotName(@PathVariable String botName) {
        return botGroupRepository.findByBotName(botName)
                .map(BotGroupProjectVo::fromEntity)
                .collectList()
                .map(list -> HttpResponseUtils.ok(Map.of("groupProjects", list, "total", list.size())));
    }

    @PostMapping
    public Mono<ResponseEntity<Map<String, Object>>> create(@RequestBody BotGroupEntity entity) {
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return botGroupRepository.save(entity)
                .flatMap(saved -> clearGroupProjectCache(saved.getBotName(), saved.getChatId())
                        .thenReturn(saved))
                .map(saved -> HttpResponseUtils.ok(Map.of("groupProject", BotGroupProjectVo.fromEntity(saved))));
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> update(@PathVariable Long id, @RequestBody BotGroupEntity entity) {
        return botGroupRepository.findById(id)
                .flatMap(existing -> {
                    existing.setChatTitle(entity.getChatTitle());
                    existing.setProjectId(entity.getProjectId());
                    existing.setProjectName(entity.getProjectName());
                    existing.setUpdatedAt(LocalDateTime.now());
                    return botGroupRepository.save(existing);
                })
                .flatMap(saved -> clearGroupProjectCache(saved.getBotName(), saved.getChatId())
                        .thenReturn(saved))
                .map(saved -> HttpResponseUtils.ok(Map.of("groupProject", BotGroupProjectVo.fromEntity(saved))))
                .defaultIfEmpty(HttpResponseUtils.notFound("Not found: " + id));
    }

    private Mono<Void> clearGroupProjectCache(String botName, Long chatId) {
        return redisTemplate.delete(
                        "bot:groupProject:" + botName + ":" + chatId,
                        "bot:groupProject:list:" + botName
                ).then();
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> delete(@PathVariable Long id) {
        return botGroupRepository.findById(id)
                .flatMap(entity -> botGroupRepository.delete(entity)
                        .flatMap(v -> clearGroupProjectCache(entity.getBotName(), entity.getChatId()))
                        .thenReturn(HttpResponseUtils.ok(Map.of("deleted", id))))
                .defaultIfEmpty(HttpResponseUtils.notFound("Not found: " + id));
    }
}
