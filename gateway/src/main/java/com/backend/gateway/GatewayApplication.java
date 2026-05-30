package com.backend.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.ApplicationContext;
import reactor.core.scheduler.Scheduler;

/**
 * 网关服务主应用类
 * ... (注释保持不变)
 */
@Slf4j
@SpringBootApplication(scanBasePackages = {
        "com.backend.gateway", // 主工程包
        "config",
        "monitor",
        "filter",
        "exception"
})
@EnableDiscoveryClient
@RefreshScope
@EnableFeignClients
public class GatewayApplication {

    /**
     * 应用主入口方法
     * @param args 命令行参数
     */
    public static void main(String[] args) {

        ApplicationContext ctx = SpringApplication.run(GatewayApplication.class, args);
        // 启动后执行额外逻辑
        initAfterStartup(ctx);
    }

    private static void initAfterStartup(ApplicationContext ctx) {
        log.info("✅ GatewayApplication started successfully!");

        try {
            // 🚨 关键修改：获取 Scheduler Bean
            // 假设 Scheduler bean 名称为 "blockingTaskScheduler"
            Scheduler scheduler = ctx.getBean("blockingTaskScheduler", Scheduler.class);
            log.info("🧵 Blocking Scheduler initialized: {}", scheduler);

            // ⚠️ 移除线程池预热逻辑：
            // Reactor Scheduler 是非阻塞和弹性管理的，没有 prestartAllCoreThreads 方法。
            log.info("ℹ️ Scheduler is elastic and managed by Reactor. Manual thread pre-starting is not required.");

        } catch (Exception e) {
            log.warn("⚠️ Could not find or initialize blockingTaskScheduler bean.", e);
        }
    }
}