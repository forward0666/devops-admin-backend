package com.backend.bot.repository;

import com.backend.bot.entity.BotGroupProjectEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface BotGroupProjectRepository extends R2dbcRepository<BotGroupProjectEntity, Long> {

    Mono<BotGroupProjectEntity> findByBotNameAndChatId(String botName, Long chatId);

    Flux<BotGroupProjectEntity> findByBotName(String botName);
}
