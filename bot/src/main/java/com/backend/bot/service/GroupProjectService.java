package com.backend.bot.service;

import com.backend.bot.entity.BotGroupEntity;
import com.backend.bot.repository.BotGroupRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class GroupProjectService {

    private final BotGroupRepository botGroupRepository;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 获取群组绑定的项目 ID。
     * 优先级：Redis 缓存 → BotGroup DB 查询 → devopsKey fallback
     */
    public Mono<Long> getProjectId(String botName, Long chatId, Long userId) {
        String cacheKey = "bot:groupProject:" + botName + ":" + chatId;
        return redisTemplate.opsForValue().get(cacheKey)
                .flatMap(cached -> {
                    try {
                        BotGroupEntity entity = objectMapper.readValue(cached, BotGroupEntity.class);
                        Long pid = entity.getProjectId();
                        if (pid != null && pid > 0) return Mono.just(pid);
                        return Mono.<Long>empty();
                    } catch (Exception e) {
                        return Mono.<Long>empty();
                    }
                })
                .switchIfEmpty(botGroupRepository.findByBotNameAndChatId(botName, chatId)
                        .flatMap(entity -> {
                            try {
                                String json = objectMapper.writeValueAsString(entity);
                                redisTemplate.opsForValue().set(cacheKey, json, Duration.ofSeconds(300)).subscribe();
                            } catch (Exception e) { log.debug("Ignored exception: {}", e.getMessage()); }
                            Long pid = entity.getProjectId();
                            if (pid != null && pid > 0) return Mono.just(pid);
                            return Mono.<Long>empty();
                        }))
                .switchIfEmpty(Mono.defer(() -> redisTemplate.opsForValue().get("bot:devops:selectedProject:" + userId)
                        .flatMap(pidStr -> Mono.just(Long.valueOf(pidStr)))))
                .switchIfEmpty(Mono.error(new RuntimeException("该群组未绑定项目")));
    }
}
