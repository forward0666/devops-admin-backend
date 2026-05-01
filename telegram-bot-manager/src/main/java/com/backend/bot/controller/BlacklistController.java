package com.backend.bot.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import network.HttpResponseUtils;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.*;

@RestController
@RequestMapping("/blacklist")
@RequiredArgsConstructor
@Slf4j
public class BlacklistController {

    private final ReactiveStringRedisTemplate redisTemplate;

    @GetMapping("/list")
    public Mono<Map<String, Object>> list(@RequestParam(required = false) String botName) {
        String pattern = botName != null && !botName.isBlank()
                ? "bot:blacklist:" + botName + ":*"
                : "bot:blacklist:*";

        return redisTemplate.keys(pattern)
                .collectList()
                .flatMap(keys -> {
                    if (keys.isEmpty()) {
                        return Mono.just(HttpResponseUtils.ok(Map.of("blacklist", List.of())));
                    }
                    return redisTemplate.opsForValue().multiGet(keys)
                            .map(values -> {
                                List<Map<String, Object>> result = new ArrayList<>();
                                for (String key : keys) {
                                    Map<String, Object> item = new HashMap<>();
                                    String[] parts = key.split(":");
                                    if (parts.length >= 4) {
                                        item.put("botName", parts[2]);
                                        item.put("chatId", parts[3]);
                                    }
                                    item.put("redisKey", key);
                                    result.add(item);
                                }
                                return HttpResponseUtils.ok(Map.of("blacklist", result));
                            });
                });
    }

    @DeleteMapping("/remove")
    public Mono<Map<String, Object>> remove(@RequestParam String botName, @RequestParam String chatId) {
        String key = "bot:blacklist:" + botName + ":" + chatId;
        return redisTemplate.delete(key)
                .map(deleted -> {
                    log.info("🔓 Removed blacklist: botName={}, chatId={}", botName, chatId);
                    return HttpResponseUtils.ok(Map.of("message", "Blacklist removed"));
                });
    }
}
