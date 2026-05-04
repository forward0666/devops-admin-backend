package com.backend.bot.exception;

import io.netty.handler.timeout.ReadTimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestControllerAdvice
@Order(-1)
@Slf4j
public class WebhookExceptionHandler {

    @ExceptionHandler(ReadTimeoutException.class)
    public Mono<ResponseEntity<Map<String, String>>> handleReadTimeout(ReadTimeoutException ex) {
        log.warn("⚠️ ReadTimeout: body not received in time, returning 200");
        return Mono.just(ResponseEntity.ok(Map.of("status", "ok")));
    }
}
