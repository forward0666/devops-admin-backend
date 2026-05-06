package com.backend.bot.repository;

import com.backend.bot.constants.BotMenuSqlConstants;
import com.backend.bot.entity.BotMenuEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface BotMenuRepository extends R2dbcRepository<BotMenuEntity, Long> {

    @Query(BotMenuSqlConstants.FIND_BY_BOT_NAME)
    Flux<BotMenuEntity> findByBotName(String botName);

    @Query(BotMenuSqlConstants.FIND_BY_BOT_NAME_ORDERED)
    Flux<BotMenuEntity> findByBotNameOrdered(String botName);

    @Query(BotMenuSqlConstants.FIND_BY_BOT_NAME_AND_MENU_KEY)
    Mono<BotMenuEntity> findByBotNameAndMenuKey(String botName, String menuKey);

    @Query(BotMenuSqlConstants.FIND_BY_BOT_NAME_AND_MENU_LEVEL_ORDERED)
    Flux<BotMenuEntity> findByBotNameAndMenuLevelOrdered(String botName, Integer menuLevel);
}
