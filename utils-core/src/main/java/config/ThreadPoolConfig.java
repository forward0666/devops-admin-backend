package config;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;
import network.ThreadPoolUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;
/**
 * 通用线程池配置（utils-core 公共组件）
 *
 * 动态根据 CPU 核心数调整线程池大小：
 * - 核心线程数 = CPU 核心数
 * - 最大线程数 = CPU 核心数 * 2
 * - 队列容量 = 1000
 * - 空闲线程保活时间 = 60s
 * - 线程命名格式 = common-worker-%d
 */
@Slf4j
@Configuration
public class ThreadPoolConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService executorService() {
        // 根据 CPU 动态计算
        int core = Runtime.getRuntime().availableProcessors();
        int max = core * 2;

        // 构建命名线程工厂
        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("worker-%d")
                .setDaemon(false)
                .build();

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                core,
                max,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000),
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        // ✅ 启动后打印一次线程池状态
        log.info("✅ ThreadPool initialized (core={}, max={}, queueSize={})", core, max, 1000);
        ThreadPoolUtils.logThreadPoolStatus(executor, "CommonWorkerPool");
        return executor;
    }
}
