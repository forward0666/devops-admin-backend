package monitor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 线程池状态定期监控
 */
@Slf4j
//@Component
@EnableScheduling
public class ThreadPoolMonitor {

    private final ThreadPoolExecutor executor;

    public ThreadPoolMonitor(ExecutorService executorService) {
        this.executor = (ThreadPoolExecutor) executorService;
    }

    @Scheduled(fixedRate = 1000) // 每 5 秒打印一次状态
    public void logThreadPoolStats() {
        log.info("🧩 [ThreadPoolMonitor] pool={}, active={}, queue={}, completed={}",
                executor.getPoolSize(),
                executor.getActiveCount(),
                executor.getQueue().size(),
                executor.getCompletedTaskCount()
        );
    }
}
