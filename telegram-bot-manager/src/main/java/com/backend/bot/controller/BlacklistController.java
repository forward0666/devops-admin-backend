package com.backend.bot.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import network.HttpResponseUtils;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.*;

@RestController
@RequestMapping("/blacklist")
@RequiredArgsConstructor
@Slf4j
public class BlacklistController {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final TypeReference<Map<String, String>> STRING_MAP_TYPE = new TypeReference<>() {};

    @GetMapping("/list")
    public Mono<ResponseEntity<Map<String, Object>>> list(@RequestParam(required = false) String botName) {
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
                                for (int i = 0; i < keys.size(); i++) {
                                    String key = keys.get(i);
                                    Map<String, Object> item = new HashMap<>();
                                    String[] parts = key.split(":");
                                    if (parts.length >= 4) {
                                        item.put("botName", parts[2]);
                                        item.put("userId", parts[3]);
                                    }
                                    item.put("redisKey", key);

                                    // 解析用户信息 JSON
                                    String raw = values.get(i);
                                    if (raw != null) {
                                        try {
                                            // raw format: "userId=123, username=John, tgUsername=@john, chatId=456"
                                            // 解析 key=value 格式
                                            String[] pairs = raw.split(", ");
                                            for (String pair : pairs) {
                                                String[] kv = pair.split("=", 2);
                                                if (kv.length == 2) {
                                                    item.put(kv[0].trim(), kv[1].trim());
                                                }
                                            }
                                        } catch (Exception e) {
                                            item.put("userInfo", raw);
                                        }
                                    }
                                    result.add(item);
                                }
                                return HttpResponseUtils.ok(Map.of("blacklist", result));
                            });
                });
    }

    @DeleteMapping("/remove")
    public Mono<ResponseEntity<Map<String, Object>>> remove(@RequestParam String botName, @RequestParam String userId) {
        String key = "bot:blacklist:" + botName + ":" + userId;
        return redisTemplate.delete(key)
                .map(deleted -> {
                    log.info("🔓 Removed blacklist: botName={}, chatId={}", botName, chatId);
                    return HttpResponseUtils.ok(Map.of("message", "Blacklist removed"));
                });
    }
}
