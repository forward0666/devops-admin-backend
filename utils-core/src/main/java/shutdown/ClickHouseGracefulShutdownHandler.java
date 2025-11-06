package shutdown;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ru.yandex.clickhouse.ClickHouseDataSource;

import java.sql.Connection;
import java.sql.SQLException;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "clickhouse", name = "enabled", havingValue = "true")
public class ClickHouseGracefulShutdownHandler {

    private final ClickHouseDataSource clickHouseDataSource;

    public ClickHouseGracefulShutdownHandler(ClickHouseDataSource clickHouseDataSource) {
        this.clickHouseDataSource = clickHouseDataSource;
    }

    @PreDestroy
    public void shutdown() {
        log.info("🚦 Closing ClickHouse DataSource...");
        try (Connection conn = clickHouseDataSource.getConnection()) {
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        } catch (SQLException e) {
            log.error("❌ Error closing ClickHouse connection", e);
        }
        log.info("✅ ClickHouse DataSource closed.");
    }
}
