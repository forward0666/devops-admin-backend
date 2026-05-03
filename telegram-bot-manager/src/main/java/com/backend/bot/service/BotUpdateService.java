package com.backend.bot.service;

import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.handler.UpdateHandlerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class BotUpdateService {

    private final UpdateHandlerFactory updateHandlerFactory;

    /**
     * 根据 Bot 实体和接收到的 Update 对象分发业务逻辑。
     *
     * @param botEntity 数据库中查找到的 Bot 实体
     * @param botUpdate Telegram Webhook Update DTO
     * @return Mono<Void> 表示处理完成
     */
    public Mono<Void> handleUpdate(BotConfigEntity botEntity, BotUpdateDto botUpdate) {

        // 使用工厂获取对应的处理器
        return updateHandlerFactory.getHandler(botUpdate)
                .map(handler -> handler.handle(botEntity, botUpdate))
                .orElseGet(() -> {
                    // 如果没有找到处理器（例如未知的更新类型），记录日志并忽略
                    log.debug("No handler found for update: {}", botUpdate);
                    return Mono.empty();
                });
    }
}