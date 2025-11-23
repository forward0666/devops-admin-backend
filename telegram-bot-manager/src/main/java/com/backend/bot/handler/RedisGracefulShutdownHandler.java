package com.backend.bot.handler;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * Redis 客户端优雅关闭
 */
@Slf4j
@Component
public class RedisGracefulShutdownHandler {

    @Autowired(required = false)
    private RedisConnectionFactory redisConnectionFactory;

    @PreDestroy
    public void shutdown() {
        if (redisConnectionFactory == null) return;

        log.info("🚦 Closing Redis connection...");
        try {
            redisConnectionFactory.getConnection().close();
        } catch (Exception e) {
            log.error("❌ Error closing Redis connection", e);
        }
        log.info("✅ Redis connection closed.");
    }
}
