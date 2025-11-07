package monitor;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@EnableScheduling
public class RuntimeMemoryMonitor {

    /**
     * 每 5 秒打印运行时内存状态
     */
    @Scheduled(fixedRate = 5000)
    public void logRuntimeMemoryStats() {
        long totalMemory = Runtime.getRuntime().totalMemory() / 1024 / 1024;
        long freeMemory = Runtime.getRuntime().freeMemory() / 1024 / 1024;
        long maxMemory = Runtime.getRuntime().maxMemory() / 1024 / 1024;
        double usedPercent = maxMemory > 0 ? ((double) (totalMemory - freeMemory) / maxMemory) * 100 : 0;

        log.info("💾 [RuntimeMemory] Total={}MB, Free={}MB, Max={}MB, Used%={} | Runtime",
                totalMemory, freeMemory, maxMemory, String.format("%.2f", usedPercent));
    }
}
