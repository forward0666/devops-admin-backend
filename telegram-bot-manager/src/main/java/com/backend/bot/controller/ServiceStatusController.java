package com.backend.bot.controller;

import filter.ActivityTrackingFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.http.ResponseEntity;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.util.*;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/serviceStatus")
public class ServiceStatusController {

    private final StringRedisTemplate stringRedisTemplate;
    private final ActivityTrackingFilter activityTrackingFilter;

    private static final String PENDING_DELETION_KEY = "bot:pendingDeletion";

    @GetMapping
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();

        // 1. 活跃请求（卡住的 webhook 请求）
        status.put("activeRequests", getActiveRequests());

        // 2. Redis 连接状态
        status.put("redis", getRedisStatus());

        // 3. 待删除消息队列
        status.put("pendingDeletions", getPendingDeletions());

        // 4. JVM 信息
        status.put("jvm", getJvmInfo());

        // 4. 线程信息
        status.put("threads", getThreadInfo());

        return ResponseEntity.ok(status);
    }

    @GetMapping("/pendingDeletions")
    public ResponseEntity<List<Map<String, Object>>> getPendingDeletionList() {
        Set<ZSetOperations.TypedTuple<String>> all =
                stringRedisTemplate.opsForZSet().rangeWithScores(PENDING_DELETION_KEY, 0, -1);

        List<Map<String, Object>> list = new ArrayList<>();
        if (all != null) {
            double now = System.currentTimeMillis() / 1000.0;
            for (ZSetOperations.TypedTuple<String> tuple : all) {
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
        }

        list.sort(Comparator.comparingDouble(m -> (double) m.get("score")));
        return ResponseEntity.ok(list);
    }

    private Map<String, Object> getRedisStatus() {
        Map<String, Object> redis = new LinkedHashMap<>();
        try {
            long start = System.currentTimeMillis();
            String testKey = "bot:health:ping";
            stringRedisTemplate.opsForValue().set(testKey, "ok");
            String result = stringRedisTemplate.opsForValue().get(testKey);
            stringRedisTemplate.delete(testKey);
            long elapsed = System.currentTimeMillis() - start;
            redis.put("status", "UP");
            redis.put("pingMs", elapsed);
            redis.put("response", result);
        } catch (Exception e) {
            redis.put("status", "DOWN");
            redis.put("error", e.getMessage());
        }
        return redis;
    }

    private Map<String, Object> getPendingDeletions() {
        Map<String, Object> info = new LinkedHashMap<>();
        Long total = stringRedisTemplate.opsForZSet().size(PENDING_DELETION_KEY);
        double now = System.currentTimeMillis() / 1000.0;
        Long expired = stringRedisTemplate.opsForZSet().count(PENDING_DELETION_KEY, 0, now);
        Long pending = total != null && expired != null ? total - expired : 0;
        info.put("total", total);
        info.put("expired", expired);
        info.put("pending", pending);
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

    private Map<String, Object> getActiveRequests() {
        Map<String, Object> info = new LinkedHashMap<>();
        Set<String> snapshot = activityTrackingFilter.getActiveRequestsSnapshot();
        info.put("count", snapshot.size());
        info.put("requests", snapshot);
        return info;
    }
}
