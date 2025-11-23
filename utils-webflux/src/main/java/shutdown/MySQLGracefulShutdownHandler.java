package shutdown;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * MySQL/R2DBC 客户端优雅关闭日志记录。
 * ❗ Spring Boot 会自动且正确地关闭 R2DBC 连接池。
 */
@Slf4j
@Component
public class MySQLGracefulShutdownHandler {

    // 移除所有 @Autowired 字段，特别是 DataSource 或 ConnectionFactory

    @PreDestroy
    public void shutdown() {
        // 记录应用开始关闭的事件，不执行任何连接操作
        log.info("🚦 MySQL/R2DBC connection management handover to Spring container...");
        log.info("✅ Application context is shutting down. MySQL/R2DBC connection pool will be closed automatically.");
    }
}