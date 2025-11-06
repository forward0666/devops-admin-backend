package com.backend.bigdata;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.ApplicationContext;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Cloudflare日志生产者应用主类
 * 中文注释：Cloudflare日志数据生产者服务的Spring Boot应用入口
 * 负责从Cloudflare获取日志数据并发送到Kafka消息队列
 * 支持服务发现和配置动态刷新
 */
@Slf4j
@SpringBootApplication(
        scanBasePackages = {
                "com.backend.bigdata", // 主工程包
                "shutdown",
                "config",
                "monitor"
        },
        exclude = {
                org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration.class,
                org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration.class,
                org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class,
                org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration.class,
                org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class
        }
)

@EnableDiscoveryClient
@RefreshScope
public class CloudflareLogsProducerApplication {

    /**
     * 应用主入口方法
     * 中文注释：启动Spring Boot应用，初始化Cloudflare日志生产者服务
     * @param args 命令行参数
     */
    public static void main(String[] args) {

//        SpringApplication.run(CloudflareLogsConsumerApplication.class, args);
        ApplicationContext ctx = SpringApplication.run(CloudflareLogsProducerApplication.class, args);
        // 启动后执行额外逻辑（如异步任务、Kafka 检查、线程池预热等）
        initAfterStartup(ctx);
    }

    private static void initAfterStartup(ApplicationContext ctx) {
        log.info("✅ CloudflareLogsProducerApplication started successfully!");

        ExecutorService executor = ctx.getBean(ExecutorService.class);
        log.info("🧵 ThreadPool initialized: {}", executor);

        // ✅ 线程池预热（提前创建核心线程）
        if (executor instanceof ThreadPoolExecutor) {
            ((ThreadPoolExecutor) executor).prestartAllCoreThreads();
            log.info("🔥 ThreadPool pre-started {} core threads",
                    ((ThreadPoolExecutor) executor).getPoolSize());
        }

    }

}
