package com.backend.monitor.job;

import com.backend.monitor.service.MonitorService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MonitorJobHandler {

    private final MonitorService monitorService;

    /**
     * Check all enabled monitor rules (equivalent to old check_domain task type)
     */
    @XxlJob("checkDomains")
    public void checkDomains() {
        log.info("[XXL-Job] checkDomains started");
        try {
            var result = monitorService.checkAll();
            XxlJobHelper.log("[XXL-Job] checkDomains result: {}", result);
        } catch (Exception e) {
            log.error("[XXL-Job] checkDomains failed", e);
            XxlJobHelper.handleFail("checkDomains failed: " + e.getMessage());
        }
    }
}