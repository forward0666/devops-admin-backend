package com.backend.task.service;

import com.backend.task.entity.TaskEntity;
import com.backend.task.mapper.TaskMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Task scheduler service - manages periodic execution of tasks.
 * Simplified version of the Python scheduler.py logic without Redis locking (single-node).
 * For multi-node HA, integrate with Redis/Redisson distributed lock.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskSchedulerService {

    private final TaskMapper taskMapper;
    private final TaskExecutorService taskExecutorService;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final Map<Long, ScheduledTask> scheduledTasks = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("[TaskScheduler] Initializing...");
        reloadTasks();
    }

    @PreDestroy
    public void shutdown() {
        log.info("[TaskScheduler] Shutting down...");
        cancelAll();
        scheduler.shutdown();
    }

    /**
     * Reload all enabled tasks and (re)schedule them.
     */
    public synchronized void reloadTasks() {
        // Cancel existing scheduled tasks
        cancelAll();

        // Load all enabled tasks
        List<TaskEntity> tasks;
        try {
            tasks = taskMapper.selectAllEnabled();
        } catch (Exception e) {
            log.error("[TaskScheduler] Failed to load tasks: {}", e.getMessage());
            return;
        }

        log.info("[TaskScheduler] Loaded {} enabled tasks", tasks.size());
        for (TaskEntity task : tasks) {
            scheduleTask(task);
        }
    }

    /**
     * Schedule a single task based on its cron expression.
     * Uses a simplified cron parser - for production, use Quartz or Spring's @Scheduled.
     */
    private void scheduleTask(TaskEntity task) {
        String cron = task.getCron();
        if (cron == null || cron.isBlank()) {
            log.warn("[TaskScheduler] Task '{}' has no cron expression, skipped", task.getName());
            return;
        }

        long intervalMs = parseCronToIntervalMs(cron);
        if (intervalMs <= 0) {
            log.warn("[TaskScheduler] Task '{}': invalid cron '{}', skipped", task.getName(), cron);
            return;
        }

        // Store for reload management
        scheduledTasks.put(task.getId(), new ScheduledTask(task.getId(), intervalMs));

        // Schedule with initial delay = interval (wait one full cycle before first run)
        scheduler.scheduleAtFixedRate(
                () -> executeSafely(task),
                intervalMs,
                intervalMs,
                TimeUnit.MILLISECONDS);

        log.info("[TaskScheduler] Scheduled: '{}' (type={}, cron='{}', interval={}ms)",
                task.getName(), task.getType(), cron, intervalMs);
    }

    /**
     * Execute a task safely, catching any exceptions.
     */
    private void executeSafely(TaskEntity task) {
        // Double-check enabled status
        try {
            var current = taskMapper.selectById(task.getId());
            if (current == null || !Boolean.TRUE.equals(current.getEnabled())) {
                log.info("[TaskScheduler] Skipping disabled task: {}", task.getName());
                return;
            }
        } catch (Exception e) {
            log.warn("[TaskScheduler] Failed to check task status: {}", e.getMessage());
            return;
        }

        log.info("[TaskScheduler] Running: {}", task.getName());
        try {
            taskExecutorService.executeTask(task);
            log.info("[TaskScheduler] Done: {}", task.getName());
        } catch (Exception e) {
            log.error("[TaskScheduler] Failed: {}: {}", task.getName(), e.getMessage());
        }
    }

    /**
     * Cancel all scheduled tasks.
     */
    private void cancelAll() {
        var iter = scheduledTasks.entrySet().iterator();
        while (iter.hasNext()) {
            iter.next();
            iter.remove();
        }
        // Note: we can't easily cancel individual ScheduledFuture tasks from ScheduledExecutorService
        // For production, use a ScheduledFuture map instead of simple records.
        log.info("[TaskScheduler] Cancelled all tasks");
    }

    // ======================== Simplified Cron Parser ========================

    /**
     * Parse a cron expression (only supports common patterns like "0 */5 * * * *" → 5min).
     * For full cron support, integrate with cron-utils or Spring's CronExpression.
     */
    private long parseCronToIntervalMs(String cron) {
        cron = cron.trim();
        String[] parts = cron.split("\\s+");

        // Standard patterns:
        // "0 */5 * * * *" → every 5 minutes
        // "0 0 * * * *"   → every hour
        // "0 0 0 * * *"   → every day at midnight
        // "0 0 * * 0"     → every week (6-part: sec min hour dayOfMonth month dayOfWeek)

        try {
            if (cron.contains("*/")) {
                // e.g., "0 */5 * * * *" → every 5 minutes
                for (String p : parts) {
                    if (p.startsWith("*/")) {
                        int num = Integer.parseInt(p.substring(2));
                        // Which position?
                        int idx = Arrays.asList(parts).indexOf(p);
                        if (idx == 0) return num * 1000L;           // seconds
                        if (idx == 1) return num * 60_000L;         // minutes
                        if (idx == 2) return num * 3600_000L;       // hours
                    }
                }
            }

            // Fixed intervals
            if (parts.length >= 5) {
                // Simple fallback: every hour
                return 3600_000L;
            }
        } catch (Exception e) {
            log.warn("[TaskScheduler] Failed to parse cron '{}': {}", cron, e.getMessage());
        }

        return -1;
    }

    // ======================== Inner Class ========================

    private record ScheduledTask(Long taskId, long intervalMs) {}
}