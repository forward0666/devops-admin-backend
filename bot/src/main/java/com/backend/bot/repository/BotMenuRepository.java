package com.backend.bot.repository;

import com.backend.bot.constants.BotMenuSqlConstants;
import com.backend.bot.entity.BotMenuEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface BotMenuRepository extends R2dbcRepository<BotMenuEntity, Long> {

    @Query(BotMenuSqlConstants.FIND_BY_BOT_TYPE_ORDERED)
    Flux<BotMenuEntity> findByBotTypeOrdered(String botType);

    @Query(BotMenuSqlConstants.FIND_BY_BOT_TYPE_AND_MENU_KEY)
    Mono<BotMenuEntity> findByBotTypeAndMenuKey(String botType, String menuKey);

    @Query(BotMenuSqlConstants.FIND_BY_BOT_TYPE_AND_MENU_LEVEL)
    Mono<BotMenuEntity> findByBotTypeAndMenuLevel(String botType, Integer menuLevel);
}
