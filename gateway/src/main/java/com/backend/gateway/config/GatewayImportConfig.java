package com.backend.gateway.config;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.ThreadFactory;

@Slf4j
@Configuration
public class GatewayImportConfig {

    @Bean(destroyMethod = "dispose")
    public Scheduler blockingTaskScheduler() {
        int core = Runtime.getRuntime().availableProcessors();
        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("blocking-worker-%d")
                .setDaemon(true)
                .build();

        Scheduler scheduler = Schedulers.newBoundedElastic(
                core * 2,
                1000,
                threadFactory,
                60
        );

        log.info("✅ BoundedElastic Scheduler initialized (max={})", core * 2);
        return scheduler;
    }
}