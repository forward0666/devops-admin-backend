package com.backend.manage;

import com.backend.manage.service.CacheService;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 管理模块主应用程序类
 *
 * 功能说明：
 * - 作为管理模块的Spring Boot应用程序入口点
 * - 集成服务发现、Feign客户端、AOP切面编程和异步处理
 * - 配置优雅关闭和线程池管理
 * - 应用启动后主动检查 MySQL / Redis / MongoDB 连接状态并打印日志
 *
 * 注解说明：
 * @SpringBootApplication - 标识为Spring Boot应用程序，包含自动配置、组件扫描等功能
 * @EnableDiscoveryClient - 启用服务发现，允许应用程序注册到 Nacos 等服务注册中心
 * @EnableAspectJAutoProxy - 启用 AspectJ 自动代理，支持切面编程
 * @EnableAsync - 启用异步方法执行，支持 @Async 注解
 * @EnableMongoRepositories - 启用 MongoDB Repository 功能
 * @MapperScan - 启用 MyBatis Mapper 接口扫描
 */
@Slf4j
@SpringBootApplication(
        scanBasePackages = {
                "com.backend.manage" // 主工程包
        })
@EnableDiscoveryClient
@EnableAspectJAutoProxy
@EnableAsync
@EnableMongoRepositories
@MapperScan("com.backend.manage.mapper")
public class ManageApplication {

    public static void main(String[] args) {

//        SpringApplication.run(CloudflareLogsConsumerApplication.class, args);
        ApplicationContext ctx = SpringApplication.run(ManageApplication.class, args);
        // 启动后执行额外逻辑（如异步任务、Kafka 检查、线程池预热等）
        initAfterStartup(ctx);
    }

    private static void initAfterStartup(ApplicationContext ctx) {
        log.info("✅ ManageApplication started successfully!");

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
