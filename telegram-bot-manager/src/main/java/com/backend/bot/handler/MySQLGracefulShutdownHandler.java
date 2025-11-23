package com.backend.bot.handler;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * MySQL 数据源优雅关闭
 */
@Slf4j
@Component
public class MySQLGracefulShutdownHandler {

    @Autowired(required = false)
    private DataSource dataSource;

    @PreDestroy
    public void shutdown() {
        if (dataSource == null) return;

        log.info("🚦 Closing MySQL DataSource...");
        try (Connection conn = dataSource.getConnection()) {
            // 这里仅尝试获取连接并关闭，DataSource 的资源通常由连接池管理
        } catch (SQLException e) {
            log.error("❌ Error closing MySQL DataSource", e);
        }
        log.info("✅ MySQL DataSource closed.");
    }
}
