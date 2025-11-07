package monitor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

@Slf4j
@Component
@EnableScheduling
public class HeapMemoryMonitor {

    private final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();

    /**
     * 每 5 秒打印堆内存状态
     */
    @Scheduled(fixedRate = 5000)
    public void logHeapMemoryStats() {
        MemoryUsage heap = memoryMXBean.getHeapMemoryUsage();
        long used = heap.getUsed() / 1024 / 1024;
        long committed = heap.getCommitted() / 1024 / 1024;
        long max = heap.getMax() / 1024 / 1024;
        double usedPercent = max > 0 ? ((double) used / max) * 100 : 0;

        log.info("🧠 [HeapMemory] Used={}MB, Committed={}MB, Max={}MB, Used%={}% | MemoryMXBean",
                used, committed, max, String.format("%.2f", usedPercent));
    }
}
