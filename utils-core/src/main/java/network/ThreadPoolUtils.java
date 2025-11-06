package network;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 通用线程池工具类
 * - 支持动态 batchSize 计算（结合 CPU / 线程池负载）
 * - 可用于 K8s 环境自动适配资源变化
 */
@Slf4j
public class ThreadPoolUtils {

    /**
     * 智能计算 batchSize：根据 CPU 核心数 + 当前线程负载动态分配
     */
    public static int calculateSmartBatchSize(ExecutorService executorService,
                                              int totalTasks,
                                              int minBatchSize,
                                              int maxBatchSize) {
        if (executorService == null || totalTasks <= 0) {
            return minBatchSize;
        }

        int cpuCores = Runtime.getRuntime().availableProcessors();
        int maxThreads = cpuCores * 2; // 默认推测最大线程数（容器下自适应）
        int activeThreads = 0;

        if (executorService instanceof ThreadPoolExecutor tpe) {
            maxThreads = tpe.getMaximumPoolSize();
            activeThreads = tpe.getActiveCount();
        }

        int availableThreads = Math.max(1, maxThreads - activeThreads);
        int batchSize = Math.max(minBatchSize, totalTasks / Math.max(cpuCores, availableThreads));

        if (maxBatchSize > 0) batchSize = Math.min(batchSize, maxBatchSize);

        log.info("🧮 Dynamic batchSize={} (total={}, cpuCores={}, active={}, available={}, max={})",
                batchSize, totalTasks, cpuCores, activeThreads, availableThreads, maxThreads);

        return batchSize;
    }

    public static int calculateSmartBatchSize(ExecutorService executorService,
                                              int totalTasks,
                                              int minBatchSize) {
        return calculateSmartBatchSize(executorService, totalTasks, minBatchSize, -1);
    }

    /**
     * 打印线程池运行状态
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
}
