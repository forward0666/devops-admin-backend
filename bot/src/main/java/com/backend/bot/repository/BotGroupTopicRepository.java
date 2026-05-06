package com.backend.bot.repository;

import com.backend.bot.constants.BotGroupSqlConstants;
import com.backend.bot.entity.BotGroupTopicEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface BotGroupTopicRepository extends R2dbcRepository<BotGroupTopicEntity, Long> {

    Flux<BotGroupTopicEntity> findByBotNameAndChatId(String botName, Long chatId);

    @Query(BotGroupSqlConstants.FIND_TOPICS_BY_BOT_NAME_AND_CHAT_ID_ORDER_BY_SORT)
    Flux<BotGroupTopicEntity> findByBotNameAndChatIdOrderBySortOrder(String botName, Long chatId);

    Mono<Void> deleteByBotNameAndChatId(String botName, Long chatId);
}
