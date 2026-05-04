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
import reactor.core.scheduler.Scheduler;
import java.time.Duration; // 引入 Scheduler

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
public class BotApplication {

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
        reactor.core.publisher.Hooks.onErrorDropped(e -> {});

        // 启动 Spring Boot 应用并获取应用上下文
        ApplicationContext ctx = SpringApplication.run(BotApplication.class, args);
        
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
        log.info("✅ BotApplication started successfully!");

        try {
            Scheduler scheduler = ctx.getBean("blockingTaskScheduler", Scheduler.class);
            log.info("🧵 Blocking Scheduler: {}", scheduler);
        } catch (Exception e) {
            log.warn("⚠️ Scheduler init failed: {}", e.getMessage());
        }

        // 预热 Redis
        try {
            org.springframework.data.redis.core.ReactiveStringRedisTemplate redis =
                    ctx.getBean(org.springframework.data.redis.core.ReactiveStringRedisTemplate.class);
            redis.opsForValue().set("bot:warmup", "ok").block(Duration.ofSeconds(5));
            redis.delete("bot:warmup").block(Duration.ofSeconds(5));
            log.info("🔥 Redis warmup OK");
        } catch (Exception e) {
            log.warn("🔥 Redis warmup failed", e);
        }

        // 预热 Scheduler 线程
        try {
            reactor.core.scheduler.Schedulers.boundedElastic().schedule(() ->
                log.info("🔥 BoundedElastic warmup OK"));
        } catch (Exception e) {
            log.warn("🔥 Scheduler warmup failed: {}", e.getMessage(), e);
        }

        // 预热 Telegram WebClient
        try {
            org.springframework.web.reactive.function.client.WebClient webClient =
                    ctx.getBean("telegramWebClient", org.springframework.web.reactive.function.client.WebClient.class);
            webClient.get().uri("/bot123/getMe")
                    .retrieve().bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(5))
                    .onErrorResume(e -> {
                        log.info("🔥 WebClient warmup OK (auth error expected)");
                        return reactor.core.publisher.Mono.empty();
                    })
                    .subscribe();
            log.info("🔥 WebClient warmup submitted");
        } catch (Exception e) {
            log.warn("🔥 WebClient warmup failed: {}", e.getMessage(), e);
        }

        // 预热整个 HTTP 管道（Jackson codec + filter chain + controller）
        try {
            String port = System.getenv().getOrDefault("SERVER_PORT", "8086");
            log.info("🔥 HTTP pipeline warmup starting to localhost:{}...", port);
            org.springframework.web.reactive.function.client.WebClient localClient =
                    org.springframework.web.reactive.function.client.WebClient.create();
            localClient.post()
                    .uri("http://localhost:" + port + "/callback/JH_OpenClaw01_Bot")
                    .header("Content-Type", "application/json")
                    .bodyValue("{\"update_id\":0}")
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(Duration.ofSeconds(10))
                    .onErrorResume(e -> {
                        log.info("🔥 HTTP pipeline warmup OK (error expected): {}", e.getMessage());
                        return reactor.core.publisher.Mono.empty();
                    })
                    .block(Duration.ofSeconds(15));
            log.info("🔥 HTTP pipeline warmup complete");
        } catch (Exception e) {
            log.warn("🔥 HTTP pipeline warmup failed", e);
        }
    }
}
