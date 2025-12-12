package com.backend.bot.repository;

import com.backend.bot.entity.BotAuthorizedChatEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
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
    
    /**
     * 根据 botConfigId 和 chatId 查询并更新授权状态。
     *
     * @param botConfigId 关联的机器人配置ID
     * @param chatId 要更新状态的聊天ID
     * @param status 新的状态 (0=禁用, 1=启用)
     * @return Mono<BotAuthorizedChatEntity> 更新后的实体，如果记录不存在则返回 Mono.empty()
     */
    Mono<BotAuthorizedChatEntity> findByBotConfigIdAndChatIdAndStatus(Long botConfigId, Long chatId, Integer status);
    
    /**
     * 根据 botConfigId 查询所有授权聊天记录
     *
     * @param botConfigId 关联的机器人配置ID
     * @return Flux<BotAuthorizedChatEntity> 授权聊天列表
     */
    Flux<BotAuthorizedChatEntity> findAllByBotConfigId(Long botConfigId);
    
    /**
     * 根据 chatId 查询所有包含该聊天ID的授权记录
     *
     * @param chatId 聊天ID
     * @return Flux<BotAuthorizedChatEntity> 授权记录列表
     */
    Flux<BotAuthorizedChatEntity> findAllByChatId(Long chatId);
}