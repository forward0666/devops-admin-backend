package com.backend.login;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@SpringBootApplication(
        scanBasePackages = {
                "com.backend.utils",
                "com.backend.login",
                "config"
        })
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.backend.login.client")
@EnableAspectJAutoProxy
@EnableAsync
@MapperScan("com.backend.login.mapper")
public class LoginApplication {

    public static void main(String[] args) {
        ApplicationContext ctx = SpringApplication.run(LoginApplication.class, args);
        initAfterStartup(ctx);
    }

    private static void initAfterStartup(ApplicationContext ctx) {
        log.info("✅ LoginApplication started successfully!");
        ExecutorService executor = ctx.getBean(ExecutorService.class);
        log.info("🧵 ThreadPool initialized: {}", executor);

        // ✅ 线程池预热（提前创建核心线程）
        if (executor instanceof ThreadPoolExecutor) {
            ((ThreadPoolExecutor) executor).prestartAllCoreThreads();
            log.info("🔥 ThreadPool pre-started {} core threads",
                    ((ThreadPoolExecutor) executor).getPoolSize());
        }

        // ✅ MySQL 预热
        try {
            ctx.getBean(javax.sql.DataSource.class).getConnection().isValid(5);
            log.info("🔥 MySQL warmup OK");
        } catch (Exception e) {
            log.warn("🔥 MySQL warmup failed: {}", e.getMessage());
        }

        // ✅ Redis 预热
        try {
            org.springframework.data.redis.core.StringRedisTemplate redis =
                    ctx.getBean(org.springframework.data.redis.core.StringRedisTemplate.class);
            redis.opsForValue().set("login:warmup", "ok");
            redis.delete("login:warmup");
            log.info("🔥 Redis warmup OK");
        } catch (Exception e) {
            log.warn("🔥 Redis warmup failed: {}", e.getMessage());
        }

        // ✅ MongoDB 预热
        try {
            org.springframework.data.mongodb.core.MongoTemplate mongoTemplate =
                    ctx.getBean(org.springframework.data.mongodb.core.MongoTemplate.class);
            mongoTemplate.getDb().listCollectionNames().first();
            log.info("🔥 MongoDB warmup OK");
        } catch (Exception e) {
            log.warn("🔥 MongoDB warmup failed: {}", e.getMessage());
        }
    }
}
