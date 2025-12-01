package com.backend.bot.handler;

import com.backend.bot.dto.BotUpdateDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 工厂类：负责根据 Update 内容分发给对应的 Handler。
 */
@Component
@RequiredArgsConstructor
public class UpdateHandlerFactory {

    // Spring 自动注入所有实现了 UpdateHandler 接口的 Bean
    private final List<UpdateHandler> handlers;

    /**
     * 获取匹配的处理器
     * @param update Telegram 更新对象
     * @return Optional<UpdateHandler>
     */
    public Optional<UpdateHandler> getHandler(BotUpdateDto update) {
        return handlers.stream()
                .filter(handler -> handler.support(update))
                .findFirst();
    }
}