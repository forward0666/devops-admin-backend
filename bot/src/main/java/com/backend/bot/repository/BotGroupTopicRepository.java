package com.backend.bot.repository;

import com.backend.bot.entity.BotGroupTopicEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface BotGroupTopicRepository extends R2dbcRepository<BotGroupTopicEntity, Long> {

    Flux<BotGroupTopicEntity> findByBotNameAndChatId(String botName, Long chatId);

    Mono<Void> deleteByBotNameAndChatId(String botName, Long chatId);
}
