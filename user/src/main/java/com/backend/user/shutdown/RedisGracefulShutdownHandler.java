package com.backend.user.shutdown;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Redis 客户端优雅关闭
 * ❗ 注意: Spring Boot 自动关闭 LettuceConnectionFactory, 此类仅用于日志记录。
 */
@Slf4j
@Component
public class RedisGracefulShutdownHandler {

    // 移除 @Autowired(required = false) private RedisConnectionFactory redisConnectionFactory;

    @PreDestroy
    public void shutdown() {
        // ❗ 移除所有手动关闭连接的逻辑，只保留日志
        log.info("🚦 Starting application shutdown sequence...");
        // ❌ 不要调用 redisConnectionFactory.getConnection().close();
        log.info("✅ Redis connection handled automatically by Spring framework.");
    }
}