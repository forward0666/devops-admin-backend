package monitor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.List;

@Slf4j
//@Component
@EnableScheduling
public class JvmRuntimeMonitor {

    private final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
    private final List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();

    @Scheduled(fixedRate = 5000)
    public void logJvmRuntimeStats() {
        // --- 线程 ---
        log.info("🧵 [Threads] Live={}, Daemon={}, Peak={}",
                threadMXBean.getThreadCount(),
                threadMXBean.getDaemonThreadCount(),
                threadMXBean.getPeakThreadCount());

        // --- GC ---
        for (GarbageCollectorMXBean gc : gcBeans) {
            log.info("🗑️ [GC] Name={}, Count={}, Time={}ms",
                    gc.getName(), gc.getCollectionCount(), gc.getCollectionTime());
        }

        // --- 可用 CPU ---
        int processors = Runtime.getRuntime().availableProcessors();
        log.info("⚙️ [JVMThreads] AvailableProcessors={}", processors);

        log.info("--------------------------------------------------------");
    }
}
