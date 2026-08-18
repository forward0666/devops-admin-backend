package com.backend.login;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.ApplicationContext;

import java.util.concurrent.Executor;

@Slf4j
@EnableAsync
@EnableScheduling
@SpringBootApplication(scanBasePackages = {
    "com.backend.utils",
    "config",
    "shutdown",
    "com.backend.login"
})
public class LoginApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoginApplication.class, args);
    }

    @PostConstruct
    public void warmUp(ApplicationContext ctx, Executor executor) {
        // 线程池预热
        if (executor instanceof java.util.concurrent.ThreadPoolExecutor) {
            ((java.util.concurrent.ThreadPoolExecutor) executor).prestartAllCoreThreads();
            log.info("🔥 ThreadPool pre-started {} core threads",
                ((java.util.concurrent.ThreadPoolExecutor) executor).getPoolSize());
        }

        // MySQL 预热
        try {
            ctx.getBean(javax.sql.DataSource.class).getConnection().isValid(5);
            log.info("🔥 MySQL warmup OK");
        } catch (Exception e) {
            log.warn("🔥 MySQL warmup failed: {}", e.getMessage());
        }

        // Redis 预热
        try {
            var redis = ctx.getBean(org.springframework.data.redis.core.StringRedisTemplate.class);
            redis.opsForValue().set("login:warmup", "ok");
            redis.delete("login:warmup");
            log.info("🔥 Redis warmup OK");
        } catch (Exception e) {
            log.warn("🔥 Redis warmup failed: {}", e.getMessage());
        }
    }
}