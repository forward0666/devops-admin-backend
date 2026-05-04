package com.backend.bot.exception;

import io.netty.handler.timeout.ReadTimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.util.Map;

@Order(-1)
@Slf4j
public class WebhookExceptionHandler implements WebExceptionHandler {

    private static final String WEBHOOK_PATH = "/callback/";

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        String path = exchange.getRequest().getPath().value();

        if (path.contains(WEBHOOK_PATH)) {
            if (ex instanceof ReadTimeoutException) {
                log.warn("⚠️ Webhook ReadTimeout: {} → 200", path);
            } else {
                log.warn("⚠️ Webhook error: {} → 200 | error: {}", path, ex.getMessage());
            }
            exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.OK);
            String body = "{\"status\":\"ok\"}";
            return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(body.getBytes())));
        }

        return Mono.error(ex);
    }
}
