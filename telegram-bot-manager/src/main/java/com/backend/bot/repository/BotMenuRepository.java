package com.backend.bot.repository;

import com.backend.bot.entity.BotMenuEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface BotMenuRepository extends ReactiveCrudRepository<BotMenuEntity, Long> {

    Flux<BotMenuEntity> findByBotName(String botName);

    @Query("SELECT * FROM bot_menu WHERE bot_name = :botName ORDER BY menu_level, sort_order")
    Flux<BotMenuEntity> findByBotNameOrderByLevelAndSort(String botName);

    Mono<BotMenuEntity> findByBotNameAndMenuKey(String botName, String menuKey);

    @Query("SELECT * FROM bot_menu WHERE bot_name = :botName AND menu_level = :menuLevel ORDER BY sort_order")
    Flux<BotMenuEntity> findByBotNameAndMenuLevel(String botName, int menuLevel);
}
