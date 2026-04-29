package com.backend.login.config;

import com.backend.login.service.audits.OperationLogService;
import com.backend.login.service.audits.OperationLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class ScheduleConfig {

    private final OperationLogService operationLogService;

    /**
     * 每天凌晨2点清理90天前的操作日志
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupExpiredOperationLogs() {
        log.info("Starting scheduled cleanup of expired operation logs");
        try {
            operationLogService.cleanupExpiredLogs(90);
            log.info("Completed scheduled cleanup of expired operation logs");
        } catch (Exception e) {
            log.error("Failed to cleanup expired operation logs: {}", e.getMessage(), e);
        }
    }
}
