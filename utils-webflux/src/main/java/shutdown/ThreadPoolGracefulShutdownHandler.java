package shutdown;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 线程池优雅关闭处理器
 */
@Slf4j
@Component
public class ThreadPoolGracefulShutdownHandler {

    @Autowired(required = false)
    private ExecutorService executorService;

    @PreDestroy
    public void shutdownThreadPool() {
        if (executorService == null) return;

        log.info("🚦 Shutting down ThreadPool...");
        executorService.shutdown(); // 不再接受新任务
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("⚠️ ThreadPool did not terminate in 30 seconds, forcing shutdown");
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.error("❌ ThreadPool shutdown interrupted", e);
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("✅ ThreadPool shutdown complete.");
    }
}
