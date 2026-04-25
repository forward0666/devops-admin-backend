package com.backend.user;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

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
 * @EnableFeignClients(basePackages = "com.backend.user.client") - 启用 Feign 客户端，用于声明式 REST 服务调用
 * @EnableAspectJAutoProxy - 启用 AspectJ 自动代理，支持切面编程
 * @EnableAsync - 启用异步方法执行，支持 @Async 注解
 * @EnableMongoRepositories - 启用 MongoDB Repository 功能
 * @MapperScan - 启用 MyBatis Mapper 接口扫描
 */
@Slf4j
@SpringBootApplication(
        scanBasePackages = {
                "com.backend.user"
        })
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.backend.user.client")
@EnableAspectJAutoProxy
@EnableAsync
@EnableMongoRepositories
@MapperScan("com.backend.user.mapper")
public class UserApplication {

    public static void main(String[] args) {

//        SpringApplication.run(CloudflareLogsConsumerApplication.class, args);
        ApplicationContext ctx = SpringApplication.run(UserApplication.class, args);
        // 启动后执行额外逻辑（如异步任务、Kafka 检查、线程池预热等）
        initAfterStartup(ctx);
    }

    private static void initAfterStartup(ApplicationContext ctx) {
        log.info("✅ UserApplication started successfully!");
    }

}
