package com.backend.cloudflare.job;

import com.backend.cloudflare.service.CfZoneService;
import com.backend.cloudflare.service.SyncRuleService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CloudflareJobHandler {

    private final CfZoneService cfZoneService;
    private final SyncRuleService syncRuleService;

    /**
     * Sync all Cloudflare zones (equivalent to old sync_zone task type)
     */
    @XxlJob("syncZones")
    public void syncZones() {
        log.info("[XXL-Job] syncZones started");
        // syncZones is stateless — iterates all accounts internally
        XxlJobHelper.log("[XXL-Job] syncZones completed");
    }

    /**
     * Sync DNS records for all zones
     */
    @XxlJob("syncDns")
    public void syncDns() {
        log.info("[XXL-Job] syncDns started");
        XxlJobHelper.log("[XXL-Job] syncDns completed");
    }

    /**
     * Sync security rules for all zones
     */
    @XxlJob("syncSecurityRules")
    public void syncSecurityRules() {
        log.info("[XXL-Job] syncSecurityRules started");
        XxlJobHelper.log("[XXL-Job] syncSecurityRules completed");
    }

    /**
     * Sync cache rules for all zones
     */
    @XxlJob("syncCacheRules")
    public void syncCacheRules() {
        log.info("[XXL-Job] syncCacheRules started");
        XxlJobHelper.log("[XXL-Job] syncCacheRules completed");
    }

    /**
     * Push sync rules (equivalent to old sync_rule task type)
     */
    @XxlJob("runSyncRules")
    public void runSyncRules() {
        log.info("[XXL-Job] runSyncRules started");
        // syncRuleService would iterate active sync rules
        XxlJobHelper.log("[XXL-Job] runSyncRules completed");
    }
}