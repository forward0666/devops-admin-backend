package com.backend.bot.repository;

import com.backend.bot.entity.BotAuthorizedChatEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * R2DBC Repository for the bot_authorized_chat table.
 */
@Repository
public interface BotAuthorizedChatRepository extends R2dbcRepository<BotAuthorizedChatEntity, Long> {

    /**
     * 根据 botConfigId 和 chatId 查询单个授权聊天记录。
     * 对应 BotCoreService 中的白名单检查逻辑。
     *
     * @param botConfigId 关联的机器人配置ID
     * @param chatId 要检查的聊天ID
     * @return Mono<BotAuthorizedChatEntity> 如果找到记录，则返回 Mono，否则返回 Mono.empty()
     */
    Mono<BotAuthorizedChatEntity> findByBotConfigIdAndChatId(Long botConfigId, Long chatId);
}