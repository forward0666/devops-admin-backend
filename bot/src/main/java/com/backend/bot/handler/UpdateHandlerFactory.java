package com.backend.bot.handler;

import com.backend.bot.dto.BotUpdateDto;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
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
     * 按照 @Order 注解的顺序检查所有处理器，返回第一个匹配的处理器
     * @param update Telegram 更新对象
     * @return Optional<UpdateHandler>
     */
    public Optional<UpdateHandler> getHandler(BotUpdateDto update) {
        return handlers.stream()
                .sorted((h1, h2) -> {
                    // 获取处理器的 @Order 注解值
                    int order1 = h1.getClass().getAnnotation(Order.class) != null ? 
                            h1.getClass().getAnnotation(Order.class).value() : Integer.MAX_VALUE;
                    int order2 = h2.getClass().getAnnotation(Order.class) != null ? 
                            h2.getClass().getAnnotation(Order.class).value() : Integer.MAX_VALUE;
                    
                    return Integer.compare(order1, order2);
                })
                .filter(handler -> handler.support(update))
                .findFirst();
    }
}