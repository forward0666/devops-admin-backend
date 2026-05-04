package com.backend.gateway.exception;

import io.netty.handler.timeout.ReadTimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
@Order(-2)
@Slf4j
public class WebhookErrorHandler implements ErrorWebExceptionHandler {

    private static final String WEBHOOK_PATH = "/bot/callback/";

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        String path = exchange.getRequest().getPath().value();

        if (path.contains(WEBHOOK_PATH)) {
            if (ex instanceof ReadTimeoutException) {
                log.warn("⚠️ Webhook ReadTimeout: {} → 200", path);
            } else {
                log.warn("⚠️ Webhook error: {} {} → 200 | error: {}", exchange.getRequest().getMethod(), path, ex.getMessage());
            }
            return writeResponse(exchange, 200);
        }

        // Non-webhook: fall through to default handling
        return Mono.error(ex);
    }

    private Mono<Void> writeResponse(ServerWebExchange exchange, int status) {
        exchange.getResponse().setStatusCode(HttpStatus.valueOf(status));
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"status\":\"ok\"}";
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
