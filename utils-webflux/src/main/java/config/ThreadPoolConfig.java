package config;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.ThreadFactory;

/**
 * 通用线程池配置（template-core 公共组件）
 *
 * 改造为 WebFlux 友好的 BoundedElastic Scheduler，专用于处理阻塞任务 (例如数据库/外部API调用)。
 */
@Slf4j
@Configuration
public class ThreadPoolConfig {

    // 建议使用单独的名称来标识这是一个 Scheduler
    @Bean(destroyMethod = "dispose") // Scheduler 的销毁方法是 dispose()
    public Scheduler blockingTaskScheduler() {
        // 根据 CPU 动态计算
        int core = Runtime.getRuntime().availableProcessors();
        // WebFlux 推荐使用 BoundedElastic，它会按需扩展，直到达到最大限制。

        // 命名格式
        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("blocking-worker-%d") // 命名修改，区分于默认的 elastic
                .setDaemon(true) // 建议设置为守护线程，利于应用关闭
                .build();

        // 1. 线程池最大线程数（对应原来的 max = core * 2）
        int maxThreads = core * 2;
        // 2. 队列容量 (对应原来的 1000)
        int queueCapacity = 1000;
        // 3. 空闲线程保活时间 (对应原来的 60L, TimeUnit.SECONDS)
        int ttlSeconds = 60;

        Scheduler scheduler = Schedulers.newBoundedElastic(
                maxThreads,
                queueCapacity,
                threadFactory,
                ttlSeconds
        );

        log.info("✅ BoundedElastic Scheduler initialized (max={}, queueCapacity={}, ttl={})",
                maxThreads, queueCapacity, ttlSeconds);

        // ⚠️ 注意：Scheduler 无法像 ThreadPoolExecutor 那样直接打印状态，因为它是动态的。
        // 使用 logThreadPoolStatus 的功能将不再适用。

        return scheduler;
    }
}