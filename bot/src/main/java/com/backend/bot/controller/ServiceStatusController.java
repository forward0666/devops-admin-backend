package com.backend.bot.controller;

import filter.ActivityTrackingFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.util.*;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/serviceStatus")
public class ServiceStatusController {

    private final ReactiveStringRedisTemplate reactiveRedisTemplate;
    private final ActivityTrackingFilter activityTrackingFilter;

    private static final String PENDING_DELETION_KEY = "bot:pendingDeletion";

    @DeleteMapping("/activeRequests")
    public Mono<ResponseEntity<Map<String, Object>>> clearActiveRequests() {
        return Mono.fromCallable(() -> {
            int cleared = activityTrackingFilter.clearActiveRequests();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("cleared", cleared);
            return result;
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
          .map(ResponseEntity::ok);
    }

    @GetMapping
    public Mono<ResponseEntity<Map<String, Object>>> getStatus() {
        return Mono.fromCallable(() -> {
            Map<String, Object> status = new LinkedHashMap<>();
            status.put("activeRequests", getActiveRequests());
            status.put("jvm", getJvmInfo());
            status.put("threads", getThreadInfo());
            return status;
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
          .zipWith(getPendingDeletionsReactive(), (status, pd) -> {
              status.put("pendingDeletions", pd);
              return status;
          })
          .zipWith(getRedisStatusReactive(), (status, redis) -> {
              status.put("redis", redis);
              return status;
          })
          .map(ResponseEntity::ok)
          .onErrorResume(e -> {
              log.error("❌ ServiceStatus error", e);
              Map<String, Object> error = new LinkedHashMap<>();
              error.put("error", e.getMessage());
              return Mono.just(ResponseEntity.ok(error));
          });
    }

    @GetMapping("/pendingDeletions")
    public Mono<ResponseEntity<List<Map<String, Object>>>> getPendingDeletionList() {
        return reactiveRedisTemplate.opsForZSet()
                .rangeWithScores(PENDING_DELETION_KEY, Range.unbounded())
                .collectList()
                .map(tuples -> {
                    double now = System.currentTimeMillis() / 1000.0;
                    List<Map<String, Object>> list = new ArrayList<>();
                    for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                        if (tuple.getValue() == null) continue;
                        String[] parts = tuple.getValue().split(":", 4);
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("chatId", parts.length > 0 ? parts[0] : "?");
                        item.put("messageId", parts.length > 1 ? parts[1] : "?");
                        item.put("userId", parts.length > 2 ? parts[2] : "?");
                        item.put("score", tuple.getScore());
                        item.put("expireAt", new Date((long) (tuple.getScore() * 1000)));
                        item.put("remainingSeconds", Math.max(0, (int) (tuple.getScore() - now)));
                        item.put("expired", tuple.getScore() <= now);
                        list.add(item);
                    }
                    list.sort(Comparator.comparingDouble(m -> (double) m.get("score")));
                    return ResponseEntity.ok(list);
                })
                .defaultIfEmpty(ResponseEntity.ok(Collections.emptyList()))
                .timeout(Duration.ofSeconds(3))
                .onErrorResume(e -> Mono.just(ResponseEntity.ok(Collections.emptyList())));
    }

    private Mono<Map<String, Object>> getRedisStatusReactive() {
        final String testKey = "bot:health:ping";
        long start = System.currentTimeMillis();
        return reactiveRedisTemplate.opsForValue().set(testKey, "ok")
                .then(reactiveRedisTemplate.opsForValue().get(testKey))
                .flatMap(result -> reactiveRedisTemplate.delete(testKey).thenReturn(result))
                .map(result -> {
                    Map<String, Object> redis = new LinkedHashMap<>();
                    redis.put("status", "UP");
                    redis.put("pingMs", System.currentTimeMillis() - start);
                    redis.put("response", result);
                    return redis;
                })
                .defaultIfEmpty(Map.of("status", "UP", "pingMs", System.currentTimeMillis() - start))
                .timeout(Duration.ofSeconds(3))
                .onErrorResume(e -> Mono.just(Map.of("status", "DOWN", "error", e.getMessage())));
    }

    private Mono<Map<String, Object>> getPendingDeletionsReactive() {
        double now = System.currentTimeMillis() / 1000.0;
        return reactiveRedisTemplate.opsForZSet().size(PENDING_DELETION_KEY)
                .zipWith(reactiveRedisTemplate.opsForZSet().count(PENDING_DELETION_KEY, Range.closed(0.0, now)))
                .map(tuple -> {
                    long total = tuple.getT1() != null ? tuple.getT1() : 0;
                    long expired = tuple.getT2() != null ? tuple.getT2() : 0;
                    Map<String, Object> info = new LinkedHashMap<>();
                    info.put("total", total);
                    info.put("expired", expired);
                    info.put("pending", total - expired);
                    return info;
                })
                .defaultIfEmpty(Map.of("total", 0, "expired", 0, "pending", 0))
                .timeout(Duration.ofSeconds(3))
                .onErrorResume(e -> Mono.just(Map.of("total", -1, "error", e.getMessage())));
    }

    private Map<String, Object> getActiveRequests() {
        Map<String, Object> info = new LinkedHashMap<>();
        Set<String> snapshot = activityTrackingFilter.getActiveRequestsSnapshot();
        info.put("count", snapshot.size());
        info.put("requests", snapshot);
        return info;
    }

    private Map<String, Object> getJvmInfo() {
        Map<String, Object> jvm = new LinkedHashMap<>();
        Runtime runtime = Runtime.getRuntime();
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        jvm.put("maxMemory", formatBytes(runtime.maxMemory()));
        jvm.put("totalMemory", formatBytes(runtime.totalMemory()));
        jvm.put("freeMemory", formatBytes(runtime.freeMemory()));
        jvm.put("usedMemory", formatBytes(runtime.totalMemory() - runtime.freeMemory()));
        jvm.put("heapUsed", formatBytes(memory.getHeapMemoryUsage().getUsed()));
        jvm.put("heapMax", formatBytes(memory.getHeapMemoryUsage().getMax()));
        jvm.put("processors", runtime.availableProcessors());
        jvm.put("uptime", formatUptime(ManagementFactory.getRuntimeMXBean().getUptime()));
        return jvm;
    }

    private Map<String, Object> getThreadInfo() {
        Map<String, Object> threads = new LinkedHashMap<>();
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        threads.put("current", threadBean.getThreadCount());
        threads.put("peak", threadBean.getPeakThreadCount());
        threads.put("daemon", threadBean.getDaemonThreadCount());
        return threads;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private String formatUptime(long ms) {
        long s = ms / 1000;
        long d = s / 86400;
        long h = (s % 86400) / 3600;
        long m = (s % 3600) / 60;
        if (d > 0) return String.format("%dd %dh %dm", d, h, m);
        if (h > 0) return String.format("%dh %dm", h, m);
        return String.format("%dm", m);
    }
}
