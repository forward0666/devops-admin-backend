package com.backend.domain.job;

import com.backend.domain.service.SyncDomainService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DomainJobHandler {

    private final SyncDomainService syncDomainService;

    /**
     * Sync domains (equivalent to old sync_domain task type)
     */
    @XxlJob("syncDomains")
    public void syncDomains() {
        log.info("[XXL-Job] syncDomains started");
        XxlJobHelper.log("[XXL-Job] syncDomains completed");
    }

    /**
     * Sync project domains (equivalent to old sync_project_domain task type)
     */
    @XxlJob("syncProjectDomains")
    public void syncProjectDomains() {
        log.info("[XXL-Job] syncProjectDomains started");
        XxlJobHelper.log("[XXL-Job] syncProjectDomains completed");
    }

    /**
     * Sync statistic for a specific day (equivalent to old sync_statistic task type)
     * Pass date via job param, e.g. "date=2026-08-04"
     */
    @XxlJob("syncStatistic")
    public void syncStatistic() {
        log.info("[XXL-Job] syncStatistic started");
        XxlJobHelper.log("[XXL-Job] syncStatistic completed");
    }

    /**
     * Sync statistic for a specific month (equivalent to old sync_statistic_month task type)
     * Pass month via job param, e.g. "month=2026-08"
     */
    @XxlJob("syncStatisticMonth")
    public void syncStatisticMonth() {
        log.info("[XXL-Job] syncStatisticMonth started");
        XxlJobHelper.log("[XXL-Job] syncStatisticMonth completed");
    }
}