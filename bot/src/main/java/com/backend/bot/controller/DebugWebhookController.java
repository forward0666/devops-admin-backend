package com.backend.bot.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequiredArgsConstructor
@Slf4j
public class DebugWebhookController {

    private final WebClient.Builder webClientBuilder;
    private final ConcurrentHashMap<String, Long> activeRequests = new ConcurrentHashMap<>();
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong stuckRequests = new AtomicLong(0);

    @PostMapping("/callback/{botName}")
    public Mono<ResponseEntity<Map<String, Object>>> handleWebhook(
            @PathVariable String botName,
            @RequestBody Map<String, Object> body) {

        String requestId = UUID.randomUUID().toString().substring(0, 8);
        long startTime = System.currentTimeMillis();
        totalRequests.incrementAndGet();
        activeRequests.put(requestId, startTime);

        log.info("[{}] 📨 Webhook received | botName={}", requestId, botName);
        log.info("[{}] 📥 Body: {}", requestId, body.keySet());

        return Mono.just(body)
                .doOnNext(b -> {
                    long elapsed = System.currentTimeMillis() - startTime;
                    log.info("[{}] ✅ Processing complete | {}ms | activeRequests={}",
                            requestId, elapsed, activeRequests.size());
                    activeRequests.remove(requestId);
                })
                .map(b -> ResponseEntity.ok()
                        .header("Connection", "close")
                        .body(Map.of(
                                "status", "ok",
                                "requestId", requestId,
                                "botName", botName,
                                "elapsedMs", System.currentTimeMillis() - startTime
                        )));
    }

    @GetMapping("/debug/status")
    public Mono<Map<String, Object>> getStatus() {
        long now = System.currentTimeMillis();
        // 清理超过 30 秒的请求
        activeRequests.entrySet().removeIf(e -> now - e.getValue() > 30000);

        return Mono.just(Map.of(
                "totalRequests", totalRequests.get(),
                "activeRequests", activeRequests.size(),
                "requests", activeRequests.entrySet().stream()
                        .map(e -> e.getKey() + " (" + (now - e.getValue()) + "ms ago)")
                        .toList()
        ));
    }

    @DeleteMapping("/debug/activeRequests")
    public Mono<Map<String, Object>> clearActiveRequests() {
        int count = activeRequests.size();
        activeRequests.clear();
        return Mono.just(Map.of("cleared", count));
    }

    /**
     * 模拟 Telegram API 调用延迟
     */
    @GetMapping("/debug/simulate/{delayMs}")
    public Mono<Map<String, Object>> simulateDelay(@PathVariable int delayMs) {
        return Mono.delay(Duration.ofMillis(delayMs))
                .map(d -> Map.of("delayMs", delayMs, "status", "done"));
    }

    /**
     * 模拟 Redis 阻塞调用
     */
    @GetMapping("/debug/blocking/{delayMs}")
    public Mono<Map<String, Object>> simulateBlocking(@PathVariable int delayMs) {
        return Mono.fromRunnable(() -> {
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .map(v -> Map.of("blockingMs", delayMs, "status", "done"));
    }

    /**
     * 模拟阻塞在 event loop 上（故意犯错）
     */
    @GetMapping("/debug/block-eventloop/{delayMs}")
    public Mono<Map<String, Object>> simulateBlockOnEventLoop(@PathVariable int delayMs) {
        log.warn("⚠️ Intentionally blocking event loop for {}ms!", delayMs);
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return Mono.just(Map.of("blockedMs", delayMs, "thread", Thread.currentThread().getName()));
    }
}
