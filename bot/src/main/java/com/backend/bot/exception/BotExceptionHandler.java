package com.backend.bot.exception;

import io.netty.handler.timeout.ReadTimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestControllerAdvice
@Order(-1) // 优先于 utils-webflux 的 GlobalException
@Slf4j
public class BotExceptionHandler {

    @ExceptionHandler(ReadTimeoutException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleReadTimeout(ReadTimeoutException ex) {
        log.warn("⚠️ ReadTimeout: client body not received in time, returning 200 to prevent TG retry");
        return Mono.just(ResponseEntity
                .status(HttpStatus.OK)
                .header("Connection", "close")
                .body(Map.of("status", "ok")));
    }
}
