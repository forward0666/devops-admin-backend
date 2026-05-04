package com.backend.bot.handler;

import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.entity.BotConfigEntity;
import reactor.core.publisher.Mono;

/**
 * 策略接口：定义处理不同类型 Telegram 更新的标准。
 */
public interface UpdateHandler {

    /**
     * 判断当前处理器是否支持该更新类型
     * @param update Telegram 更新对象
     * @return true 如果支持
     */
    boolean support(BotUpdateDto update);

    /**
     * 执行具体的业务逻辑
     * @param botEntity 机器人配置实体
     * @param update Telegram 更新对象
     * @return Mono<Void>
     */
    Mono<Void> handle(BotConfigEntity botEntity, BotUpdateDto update);
}