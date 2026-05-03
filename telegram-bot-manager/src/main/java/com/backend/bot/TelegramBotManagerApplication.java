package com.backend.bot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.ApplicationContext;
import reactor.core.scheduler.Scheduler; // 引入 Scheduler

/**
 * Telegram Bot 管理系统主启动类
 * 
 * 该应用是一个基于 Spring Boot WebFlux 的响应式 Telegram Bot 管理平台，
 * 提供多 Bot 管理、Webhook 处理、白名单机制、交互式菜单等功能。
 * 
 * 技术栈：Spring Boot 3.x + Java 21 + WebFlux + R2DBC + Redis + MySQL
 * 架构模式：响应式编程 (Reactive Programming)
 * 
 * @author Backend Team
 * @version 1.0.0
 */
@EnableAsync
@EnableScheduling
@SpringBootApplication(
        scanBasePackages = {
                "com.backend.bot", // 主工程包 - 扫描所有 Telegram Bot 相关组件
                "shutdown",       // 优雅关闭处理包
                "config",         // 全局配置类包
                "monitor",        // 监控相关包
                "filter",         // 过滤器包
                "exception"       // 全局异常处理包
        }
)

@Slf4j                              // 启用 Lombok 日志功能
@EnableDiscoveryClient               // 启用服务发现客户端，注册到 Nacos
@RefreshScope                       // 启用配置动态刷新功能，支持 Nacos 配置中心
public class TelegramBotManagerApplication {

    /**
     * 应用程序主入口方法
     * 
     * 启动 Spring Boot 应用，并在应用启动完成后执行初始化操作。
     * 使用 SpringApplication.run() 方法启动应用上下文，
     * 然后调用 initAfterStartup 方法执行启动后初始化逻辑。
     * 
     * @param args 命令行参数，可传递配置项覆盖等
     */
    public static void main(String[] args) {

        // 启动 Spring Boot 应用并获取应用上下文
        ApplicationContext ctx = SpringApplication.run(TelegramBotManagerApplication.class, args);
        
        // 执行启动后的初始化逻辑
        initAfterStartup(ctx);
    }

    /**
     * 应用启动后初始化方法
     * 
     * 在 Spring 应用上下文加载完成后执行，负责初始化关键的系统组件。
     * 主要功能：
     * 1. 获取响应式调度器 Bean 用于处理阻塞任务
     * 2. 记录初始化状态信息
     * 3. 提供异常处理机制确保启动流程的健壮性
     * 
     * @param ctx Spring 应用上下文，用于获取 Bean 实例
     */
    private static void initAfterStartup(ApplicationContext ctx) {
        log.info("✅ TelegramBotManagerApplication started successfully!");

        try {
            // 🚨 关键修改：获取响应式调度器 Bean
            // 该调度器用于处理可能阻塞的 IO 操作，确保响应式主流程不受影响
            // Bean 名称必须与 ThreadPoolConfig 中定义的名称一致
            Scheduler scheduler = ctx.getBean("blockingTaskScheduler", Scheduler.class);
            log.info("🧵 Blocking Scheduler initialized: {}", scheduler);

            // ⚠️ 说明：移除线程池预热逻辑
            // Reactor Scheduler 是弹性管理的，不需要像传统线程池那样预启动线程
            // 它会根据需要动态创建和回收线程，更适合响应式编程范式
            log.info("ℹ️ Scheduler is elastic and managed by Reactor. Manual thread pre-starting is not required.");

        } catch (Exception e) {
            // 记录警告信息，但不影响应用启动流程
            // 即使调度器初始化失败，应用仍然可以运行，只是可能影响某些需要阻塞操作的功能
            log.warn("⚠️ Could not find or initialize blockingTaskScheduler bean.", e);
        }
    }
}