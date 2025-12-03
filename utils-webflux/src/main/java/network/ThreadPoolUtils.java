package network;

import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import reactor.core.scheduler.Scheduler; // 引入 Scheduler

/**
 * 通用工具类 (针对非阻塞改造，移除线程池负载计算)
 */
@Slf4j
public class ThreadPoolUtils {

    // ⚠️ 移除 calculateSmartBatchSize 及其重载方法。
    // 在 WebFlux 中，应使用 buffer() 操作符代替手动计算 batch size。

    /**
     * 【仅保留打印状态，不用于 WebFlux】
     * 打印传统线程池运行状态
     */
    public static void logThreadPoolStatus(ThreadPoolExecutor executor, String poolName) {
        if (executor == null) return;
        log.info("📊 [{}] Pool status: active={}, queue={}, completed={}, largest={}, max={}",
                poolName,
                executor.getActiveCount(),
                executor.getQueue().size(),
                executor.getCompletedTaskCount(),
                executor.getLargestPoolSize(),
                executor.getMaximumPoolSize());
    }

    // ℹ️ 建议：可以添加一个辅助方法，帮助开发者正确地在 Mono/Flux 链中切换 Scheduler
    public static void executeBlockingTask(Scheduler scheduler) {
        log.warn("⚠️ executeBlockingTask 辅助方法占位。在实际业务中，请使用 mono.subscribeOn(scheduler)");
    }
}