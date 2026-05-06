package com.backend.bot.repository;

import com.backend.bot.entity.BotGroupEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface BotGroupRepository extends R2dbcRepository<BotGroupEntity, Long> {

    Mono<BotGroupEntity> findByBotNameAndChatId(String botName, Long chatId);

    Flux<BotGroupEntity> findByBotName(String botName);

    Mono<Boolean> existsByBotNameAndChatId(String botName, Long chatId);
}
