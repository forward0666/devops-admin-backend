package com.backend.bot.repository;

import com.backend.bot.constants.BotMenuSqlConstants;
import com.backend.bot.entity.BotMenuEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface BotMenuRepository extends ReactiveCrudRepository<BotMenuEntity, Long> {

    Flux<BotMenuEntity> findByBotName(String botName);

    @Query(BotMenuSqlConstants.FIND_BY_BOT_NAME_ORDER_BY_LEVEL_AND_SORT)
    Flux<BotMenuEntity> findByBotNameOrderByLevelAndSort(String botName);

    Mono<BotMenuEntity> findByBotNameAndMenuKey(String botName, String menuKey);

    @Query(BotMenuSqlConstants.FIND_BY_BOT_NAME_AND_MENU_LEVEL)
    Flux<BotMenuEntity> findByBotNameAndMenuLevel(String botName, int menuLevel);
}
