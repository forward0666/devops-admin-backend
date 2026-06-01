package com.backend.bot.repository;

import com.backend.bot.constants.BotConfigSqlConstants;
import com.backend.bot.entity.BotConfigEntity;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface BotRepository extends ReactiveCrudRepository<BotConfigEntity, Long> {

    @Query(BotConfigSqlConstants.SELECT_BY_BOT_NAME_LIMIT_ONE)
    Mono<BotConfigEntity> findByBotName(String botName);

    @Modifying
    @Query(BotConfigSqlConstants.DELETE_BY_BOT_NAME)
    Mono<Void> deleteByBotName(String botName);

    @Query(BotConfigSqlConstants.EXISTS_BY_BOT_NAME)
    Mono<Integer> countByBotName(String botName);

    @Query(BotConfigSqlConstants.EXISTS_BY_BOT_USERNAME)
    Mono<Integer> countByBotUsername(String botUsername);

    @Modifying
    @Query(BotConfigSqlConstants.UPDATE_WEBHOOK_URL)
    Mono<Void> updateWebhookUrl(String webhookUrl, String botName);

    @Modifying
    @Query(BotConfigSqlConstants.CLEAR_WEBHOOK_URL)
    Mono<Void> clearWebhookUrl(String botName);
}
