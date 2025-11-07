package monitor;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.List;
import java.util.Properties;

@Slf4j
@Component
public class JvmStartupInfoPrinter {

    @PostConstruct
    public void logJvmStartupInfo() {
        log.info("🧠 [JVMInfo] ---- JVM Startup Parameters ----");

        // JVM 启动参数
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        List<String> inputArgs = runtimeMXBean.getInputArguments();
        inputArgs.forEach(arg -> log.info("   ⚙️ {}", arg));

        // GC 信息
        String gcName = ManagementFactory.getGarbageCollectorMXBeans()
                .stream()
                .map(GarbageCollectorMXBean::getName)
                .reduce((a, b) -> a + ", " + b)
                .orElse("Unknown");
        log.info("🧩 [GC] {}", gcName);

        // 系统信息
        Properties props = System.getProperties();
        log.info("🧩 [CPU] Available processors: {}", Runtime.getRuntime().availableProcessors());
        log.info("🧩 [OS] {} {}", props.getProperty("os.name"), props.getProperty("os.version"));
        log.info("🧩 [Java] {}", props.getProperty("java.version"));

        // 启动时运行时内存
        long totalMemory = Runtime.getRuntime().totalMemory() / 1024 / 1024;
        long freeMemory = Runtime.getRuntime().freeMemory() / 1024 / 1024;
        long maxMemory = Runtime.getRuntime().maxMemory() / 1024 / 1024;
        double usedPercent = maxMemory > 0 ? ((double) (totalMemory - freeMemory) / maxMemory) * 100 : 0;

        log.info("💾 [RuntimeMemory] Total={}MB, Free={}MB, Max={}MB, Used%={} | Startup",
                totalMemory, freeMemory, maxMemory, String.format("%.2f", usedPercent));

        log.info("-------------------------------------------");
    }
}
