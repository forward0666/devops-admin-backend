package shutdown;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * MongoDB 客户端优雅关闭日志记录。
 * ❗ Spring Boot 会自动且正确地关闭 MongoClient 连接。
 */
@Slf4j
@Component
public class MongoDBGracefulShutdownHandler {

    // 无需 autowire MongoClient 或 MongoDatabaseFactory

    @PreDestroy
    public void shutdown() {
        // 记录应用开始关闭的事件
        log.info("🚦 MongoDB client management handover to Spring container...");
        log.info("✅ Application context is shutting down. MongoDB client connection pool will be closed automatically.");
    }
}