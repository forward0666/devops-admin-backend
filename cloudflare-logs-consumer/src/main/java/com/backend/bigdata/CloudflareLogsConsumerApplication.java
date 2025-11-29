package com.backend.bigdata;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Cloudflare日志消费者应用程序主类
 *
 * 功能说明：
 * 1. 作为Spring Boot应用程序的入口点
 * 2. 消费Kafka中的Cloudflare日志数据
 * 3. 将日志数据批量插入到ClickHouse数据库
 * 4. 支持服务发现和配置刷新
 *
 * 核心注解说明：
 * @SpringBootApplication - 标识这是一个Spring Boot应用程序，包含自动配置、组件扫描等功能
 * @EnableDiscoveryClient - 启用服务发现客户端，允许服务注册到服务注册中心
 * @RefreshScope - 启用配置刷新功能，支持动态更新配置
 * @MapperScan - 扫描MyBatis mapper接口，指定mapper包路径
 *
 * 模块职责：
 * - Kafka消费者：批量消费Cloudflare HTTP请求日志
 * - 数据处理：解析JSON日志数据并扁平化处理
 * - 数据存储：将处理后的数据批量插入ClickHouse
 * - 监控：提供消费状态监控和错误处理
 *
 * 技术栈：
 * - Spring Boot 2.x
 * - Spring Kafka
 * - MyBatis
 * - ClickHouse JDBC
 * - Jackson JSON处理
 */
@Slf4j
@SpringBootApplication(
        scanBasePackages = {
        "com.backend.bigdata", // 主工程包
        "shutdown",
        "config",
        "monitor"
        }
//        ,
//        exclude = {
//        org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration.class,
//        org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration.class,
//        org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class,
//        org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration.class
//        org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class
//        }
        )
@EnableDiscoveryClient
@RefreshScope
@EnableScheduling
@MapperScan({"com.backend.bigdata.mapper"})
public class CloudflareLogsConsumerApplication {

    /**
     * 应用程序主入口方法
     * <p>
     * 功能说明：
     * 1. 启动Spring Boot应用程序
     * 2. 初始化Kafka消费者配置
     * 3. 注册MyBatis mapper接口
     * 4. 启动日志消费服务
     *
     * @param args 命令行参数，可用于配置应用程序行为
     */
    public static void main(String[] args) {
//        SpringApplication.run(CloudflareLogsConsumerApplication.class, args);
        ApplicationContext ctx = SpringApplication.run(CloudflareLogsConsumerApplication.class, args);

        // ✅ 示例：提交线程池任务验证（可以删掉）
//        ExecutorService executorService = ctx.getBean(ExecutorService.class);
//        for (int i = 0; i < 5; i++) {
//            int id = i;
//            executorService.submit(() -> {
//                try {
//                    System.out.println("🧵 Task " + id + " running in thread: " + Thread.currentThread().getName());
//                    Thread.sleep(3000);
//                    System.out.println("✅ Task " + id + " done.");
//                } catch (InterruptedException e) {
//                    Thread.currentThread().interrupt();
//                }
//            });
//
//        }
        // 启动后执行额外逻辑（如异步任务、Kafka 检查、线程池预热等）
        initAfterStartup(ctx);
    }

    private static void initAfterStartup(ApplicationContext ctx) {
        log.info("✅ CloudflareLogsConsumerApplication started successfully!");

        ExecutorService executor = ctx.getBean(ExecutorService.class);
        log.info("🧵 ThreadPool initialized: {}", executor);

        // ✅ 线程池预热（提前创建核心线程）
        if (executor instanceof ThreadPoolExecutor) {
            ((ThreadPoolExecutor) executor).prestartAllCoreThreads();
            log.info("🔥 ThreadPool pre-started {} core threads",
                    ((ThreadPoolExecutor) executor).getPoolSize());
        }

        boolean hasClickHouse = ctx.containsBean("clickHouseDataSource");
        log.info("🧩 ClickHouse bean loaded: {}", hasClickHouse);
    }


}
