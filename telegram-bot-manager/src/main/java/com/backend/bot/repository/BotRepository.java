package com.backend.bot.repository;

import com.backend.bot.entity.BotEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface BotRepository extends ReactiveCrudRepository<BotEntity, Long> {

    /**
     * ✅ 修正点 1: 确保方法名与实体字段名完全匹配 (findByBotName)
     */
    Mono<BotEntity> findByBotName(String botName);

    /**
     * ✅ 修正点 2: 必须将 findByToken 更改为 findByBotToken
     * 以匹配 BotEntity 中的 private String botToken;
     */
    Mono<BotEntity> findByBotToken(String token);
}