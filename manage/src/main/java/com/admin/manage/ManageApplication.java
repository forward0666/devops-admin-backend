package com.admin.manage;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.Executor;
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
 * @EnableFeignClients - 启用 Feign 客户端，用于声明式 REST 服务调用
 * @EnableAspectJAutoProxy - 启用 AspectJ 自动代理，支持切面编程
 * @EnableAsync - 启用异步方法执行，支持 @Async 注解
 * @EnableMongoRepositories - 启用 MongoDB Repository 功能
 */
@Slf4j
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@EnableAspectJAutoProxy
@EnableAsync
@EnableMongoRepositories
public class ManageApplication {

    /**
     * 应用程序主入口方法
     *
     * 功能说明：
     * - 初始化 Spring 应用程序上下文
     * - 配置优雅关闭钩子，确保应用程序关闭时完成所有任务
     * - 启动应用程序并运行所有配置的 Bean
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(ManageApplication.class);

        // 配置优雅关闭，确保应用程序关闭时注册关闭钩子
        app.setRegisterShutdownHook(true);

        app.run(args);
    }

    /**
     * 创建 RestTemplate bean 用于 HTTP 请求
     *
     * 功能说明：
     * - 提供同步的 HTTP 客户端功能
     * - 用于调用其他微服务的 REST API
     * - 支持各种 HTTP 方法（GET、POST、PUT、DELETE 等）
     *
     * @return RestTemplate 实例
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    /**
     * 配置任务执行器用于异步操作
     *
     * 功能说明：
     * - 专门用于操作日志记录等异步任务
     * - 配置核心线程数、最大线程数和队列容量
     * - 支持优雅关闭，等待任务完成
     * - 设置线程名前缀便于监控和调试
     *
     * 配置参数说明：
     * - corePoolSize: 2 - 核心线程数，保持活跃的最小线程数
     * - maxPoolSize: 5 - 最大线程数，线程池允许的最大线程数
     * - queueCapacity: 100 - 队列容量，等待执行的任务队列大小
     * - threadNamePrefix: "async-operation-log-" - 线程名前缀，便于日志追踪
     * - waitForTasksToCompleteOnShutdown: true - 关闭时等待任务完成
     * - awaitTerminationSeconds: 60 - 等待任务完成的最大秒数
     *
     * @return 配置好的任务执行器
     */
    @Bean("taskExecutor")
    @Primary
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor() {
            @Override
            public void execute(Runnable task) {
                log.debug("➡️ 提交任务到线程池");
                super.execute(task);
            }
        };

        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("async-operation-log-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        // 增加日志装饰器，打印执行情况
        executor.setTaskDecorator(runnable -> () -> {
            log.debug("▶️ 任务开始执行，线程 = {}", Thread.currentThread().getName());
            try {
                runnable.run();
                log.debug("✅ 任务执行完成，线程 = {}", Thread.currentThread().getName());
            } catch (Exception e) {
                log.error("❌ 任务执行异常，线程 = {}", Thread.currentThread().getName(), e);
                throw e;
            }
        });

        // 拒绝策略日志
        executor.setRejectedExecutionHandler((r, exec) -> {
            log.error("🚨 任务被拒绝: ActiveCount={}, QueueSize={}, PoolSize={}",
                    exec.getActiveCount(),
                    exec.getQueue().size(),
                    exec.getPoolSize());
            new ThreadPoolExecutor.AbortPolicy().rejectedExecution(r, exec);
        });


        executor.initialize();
        log.info("✅ 异步任务线程池初始化完成: corePoolSize={}, maxPoolSize={}, queueCapacity={}",
                executor.getCorePoolSize(),
                executor.getMaxPoolSize(),
                executor.getThreadPoolExecutor().getQueue().remainingCapacity());
        return executor;
    }

    /**
     * 应用启动后执行数据库、缓存、中间件连接检查
     *
     * 功能说明：
     * - 启动完成后自动运行，不影响主线程启动速度
     * - 主动检测 MySQL / Redis / MongoDB 的可用性
     * - 如果连接失败，会打印 ERROR 日志；成功则打印 INFO 日志
     *
     * 优点：
     * - 无需开启 DEBUG 日志即可知道初始化状态
     * - 便于运维人员快速定位服务启动异常
     *
     * @param jdbcTemplate    用于检测 MySQL 数据库连接
     * @param redisTemplate   用于检测 Redis 连接
     * @param mongoTemplate   用于检测 MongoDB 连接
     * @return ApplicationRunner 实例
     */
//    @Bean
//    public ApplicationRunner initCheckRunner(
//            JdbcTemplate jdbcTemplate,
//            StringRedisTemplate redisTemplate,
//            MongoTemplate mongoTemplate,
//            RedisConnectionFactory redisConnectionFactory,
//            MongoClient mongoClient
//
//    ) {
//        return args -> {
//            // 检查 MySQL
//            try {
//                jdbcTemplate.execute("SELECT 1");
//                log.info("✅ [初始化] MySQL [{}] 连接正常", jdbcTemplate.getDataSource().getConnection().getMetaData().getURL());
//
//            } catch (Exception e) {
//                log.error("❌ [初始化] MySQL 数据库连接失败", e);
//            }
//
//            // 检查 Redis
//            try {
//                redisTemplate.hasKey("health-check");
//                if (redisConnectionFactory instanceof LettuceConnectionFactory lettuce) {
//                    log.info("✅ [初始化] Redis 连接正常 -> {}:{} db={}",
//                            lettuce.getHostName(),
//                            lettuce.getPort(),
//                            lettuce.getDatabase());
//                } else {
//                    log.info("✅ [初始化] Redis 连接正常");
//                }
//            } catch (Exception e) {
//                log.error("❌ [初始化] Redis 连接失败", e);
//            }
//
//            // 检查 MongoDB
//            // MongoDB
//            try {
//                MongoDatabase db = mongoTemplate.getDb();
//                String dbName = db.getName();
//                String hosts = mongoClient.getClusterDescription()
//                        .getClusterSettings()
//                        .getHosts()
//                        .toString(); // e.g. [10.10.72.40:32290]
//                log.info("✅ [初始化] MongoDB [{}] [{}] 连接正常", dbName, hosts);
//            } catch (Exception e) {
//                log.error("❌ [初始化] MongoDB 连接失败", e);
//            }
//        };
//    }
}
