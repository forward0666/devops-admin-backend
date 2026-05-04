package com.backend.bot.handler;

import com.backend.bot.context.HandlerContext;
import com.backend.bot.dto.BotUpdateDto;
import com.backend.bot.entity.BotConfigEntity;
import com.backend.bot.util.LogUtils;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

@Slf4j
public abstract class AbstractUpdateHandler implements UpdateHandler {

    @Override
    public final Mono<Void> handle(BotConfigEntity botEntity, BotUpdateDto update) {
        return Mono.deferContextual(contextView -> {
                    final String logPrefix = LogUtils.prepareMdcAndGetPrefix(contextView);
                    HandlerContext context = new HandlerContext(botEntity, update);

                    log.info("{}✅ Accepted update with handler: {}", logPrefix, this.getClass().getSimpleName());

                    return handleUpdate(context, logPrefix, contextView)
                            .contextWrite(contextView);
                })
                .doOnError(e -> log.error("❌ Unhandled error in {} pipeline.", this.getClass().getSimpleName(), e))
                .then()
                .doFinally(LogUtils::clearMDC);
    }

    protected abstract Mono<Void> handleUpdate(HandlerContext context, String logPrefix, ContextView contextView);
}