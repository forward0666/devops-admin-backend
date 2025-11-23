package shutdown;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ru.yandex.clickhouse.ClickHouseDataSource; // 保持导入，但我们不操作它

/**
 * ClickHouse DataSource 优雅关闭日志记录。
 * ❗ 移除手动关闭逻辑，信任 Spring 自动关闭 DataSource Bean。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "clickhouse", name = "enabled", havingValue = "true")
public class ClickHouseGracefulShutdownHandler {

    // 保持注入，但我们不再使用它进行操作
    private final ClickHouseDataSource clickHouseDataSource;

    public ClickHouseGracefulShutdownHandler(ClickHouseDataSource clickHouseDataSource) {
        this.clickHouseDataSource = clickHouseDataSource;
    }

    @PreDestroy
    public void shutdown() {
        // 仅记录应用开始关闭的事件
        log.info("🚦 ClickHouse DataSource management handover to Spring container...");

        // 移除 try-catch 和 conn.close() 逻辑

        log.info("✅ Application context is shutting down. ClickHouse DataSource will be closed automatically.");
    }
}