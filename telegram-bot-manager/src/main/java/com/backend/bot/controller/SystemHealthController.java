package com.backend.bot.controller;

import com.backend.bot.repository.BotRepository;
import com.backend.bot.service.BotClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import network.HttpResponseUtils;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/health")
public class SystemHealthController {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final DatabaseClient databaseClient;
    private final BotClientService botClientService;
    private final BotRepository botRepository;

    @GetMapping("/cache")
    public Mono<ResponseEntity<Map<String, Object>>> getCacheStatus() {
        String testKey = "health:check:" + System.currentTimeMillis();
        return redisTemplate.opsForValue().set(testKey, "ok", Duration.ofSeconds(5))
                .flatMap(success -> redisTemplate.delete(testKey).then(Mono.just(success)))
                .map(ok -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("status", "healthy");
                    data.put("redis", "connected");
                    data.put("timestamp", Instant.now());
                    return HttpResponseUtils.ok(data);
                })
                .onErrorResume(e -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("status", "unhealthy");
                    data.put("redis", "disconnected");
                    data.put("error", e.getMessage());
                    return Mono.just(HttpResponseUtils.serviceUnavailable("Cache unavailable", data));
                });
    }

    @GetMapping("/database")
    public Mono<ResponseEntity<Map<String, Object>>> getDatabaseStatus() {
        return databaseClient.sql("SELECT 1 as health_check")
                .map((row, metadata) -> row.get("health_check", Integer.class))
                .first()
                .timeout(Duration.ofSeconds(5))
                .map(result -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("status", "healthy");
                    data.put("database", "connected");
                    data.put("queryResult", result);
                    data.put("timestamp", Instant.now());
                    return HttpResponseUtils.ok(data);
                })
                .onErrorResume(e -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("status", "unhealthy");
                    data.put("database", "disconnected");
                    data.put("error", e.getMessage());
                    return Mono.just(HttpResponseUtils.serviceUnavailable("Database unavailable", data));
                });
    }

    @GetMapping("/telegram-api")
    public Mono<ResponseEntity<Map<String, Object>>> getTelegramApiStatus(
            @RequestParam(required = false) String botName) {
        return botRepository.findAll()
                .filter(bot -> botName == null || bot.getBotName().equals(botName))
                .next()
                .flatMap(bot -> botClientService.getWebhookInfo(bot.getBotToken()))
                .map(info -> {
                    Map<String, Object> data = new HashMap<>();
                    boolean ok = info.containsKey("ok") && (Boolean) info.get("ok");
                    data.put("status", ok ? "healthy" : "unhealthy");
                    data.put("telegramApi", ok ? "connected" : "error");
                    data.put("timestamp", Instant.now());
                    return HttpResponseUtils.ok(data);
                })
                .defaultIfEmpty(HttpResponseUtils.ok(Map.of(
                        "status", "unknown", "telegramApi", "no_bot_configured", "timestamp", Instant.now())));
    }

    @GetMapping("/metrics/bots")
    public Mono<ResponseEntity<Map<String, Object>>> getBotMetrics() {
        return botRepository.findAll()
                .collectList()
                .map(bots -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("totalBots", bots.size());
                    data.put("activeBots", bots.stream().filter(b -> b.isActive()).count());
                    data.put("inactiveBots", bots.stream().filter(b -> !b.isActive()).count());
                    Map<String, Long> byType = new HashMap<>();
                    bots.forEach(b -> {
                        String type = b.getBotType() != null ? b.getBotType().getDbValue() : "unknown";
                        byType.merge(type, 1L, Long::sum);
                    });
                    data.put("botsByType", byType);
                    data.put("timestamp", Instant.now());
                    return HttpResponseUtils.ok(data);
                });
    }

    @GetMapping("/metrics/messages")
    public Mono<ResponseEntity<Map<String, Object>>> getMessageMetrics(
            @RequestParam(defaultValue = "day") String period) {
        // TODO: 需要消息计数存储（Redis/MongoDB），暂时返回空
        Map<String, Object> data = new HashMap<>();
        data.put("period", period);
        data.put("totalMessages", 0);
        data.put("successfulMessages", 0);
        data.put("failedMessages", 0);
        data.put("averageProcessingTime", "0ms");
        data.put("timestamp", Instant.now());
        return Mono.just(HttpResponseUtils.ok(data));
    }

    @GetMapping("/overall")
    public Mono<ResponseEntity<Map<String, Object>>> getOverallHealth() {
        Mono<Boolean> dbCheck = databaseClient.sql("SELECT 1").fetch().first()
                .map(r -> true).defaultIfEmpty(false).onErrorReturn(false);
        Mono<Boolean> redisCheck = redisTemplate.hasKey("health:overall:" + System.currentTimeMillis())
                .onErrorReturn(false);

        return Mono.zip(dbCheck, redisCheck)
                .map(tuple -> {
                    boolean dbOk = tuple.getT1();
                    boolean redisOk = tuple.getT2();
                    Map<String, Object> data = new HashMap<>();
                    data.put("overallStatus", dbOk && redisOk ? "healthy" : "degraded");
                    data.put("database", dbOk ? "healthy" : "unhealthy");
                    data.put("cache", redisOk ? "healthy" : "unhealthy");
                    data.put("timestamp", Instant.now());
                    return HttpResponseUtils.ok(data);
                });
    }
}
