package com.backend.task.service;

import com.backend.task.entity.TaskEntity;
import com.backend.task.mapper.TaskMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Task executor service - dispatches tasks by type to corresponding handlers.
 * This is a simplified adaptation of the Python executor.py logic.
 * In production, these would make HTTP calls to other microservices via Feign/RestTemplate.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskExecutorService {

    private final TaskMapper taskMapper;
    private final ObjectMapper objectMapper;

    public void executeTask(TaskEntity task) {
        if (task == null) return;
        String type = task.getType();
        Map<String, Object> config = parseConfig(task.getConfig());

        log.info("[TaskExecutor] Executing task '{}' (type={})", task.getName(), type);

        try {
            switch (type) {
                case "check_domain"       -> runCheckDomain(task, config);
                case "sync_zone"          -> runSyncZone(task, config);
                case "sync_dns"           -> runSyncDns(task, config);
                case "sync_security"      -> runSyncSecurity(task, config);
                case "sync_cache"         -> runSyncCache(task, config);
                case "sync_domain"        -> runSyncDomain(task, config);
                case "sync_rule"          -> runSyncRule(task, config);
                case "sync_project_domain" -> runSyncProjectDomain(task, config);
                case "sync_statistic"     -> runSyncStatistic(task, config);
                case "sync_statistic_month" -> runSyncStatisticMonth(task, config);
                default:
                    log.warn("[TaskExecutor] Unknown task type: {}", type);
            }
            updateTaskStatus(task.getId(), "success");
        } catch (Exception e) {
            log.error("[TaskExecutor] Task '{}' failed: {}", task.getName(), e.getMessage(), e);
            updateTaskStatus(task.getId(), "failed");
        }
    }

    /**
     * Check domains: calls monitor service to check all or specific rules.
     * In Python: POST {monitor_url}/rules/{rule_id}/check for each rule in config.rule_ids.
     */
    private void runCheckDomain(TaskEntity task, Map<String, Object> config) {
        @SuppressWarnings("unchecked")
        List<Integer> ruleIds = (List<Integer>) config.getOrDefault("rule_ids", List.of());
        log.info("[TaskExecutor] check_domain: rule_ids={}", ruleIds);
        // In production: RestTemplate/Fegin call to monitor service
        for (Object rid : ruleIds) {
            log.info("[TaskExecutor]   -> POST /monitor/rules/{}/check", rid);
        }
    }

    /**
     * Sync zones from Cloudflare: calls cloudflare service's zone sync for each account.
     */
    private void runSyncZone(TaskEntity task, Map<String, Object> config) {
        @SuppressWarnings("unchecked")
        List<String> accountIds = (List<String>) config.getOrDefault("account_ids", List.of());
        log.info("[TaskExecutor] sync_zone: account_ids={}", accountIds);
    }

    /**
     * Sync DNS records from Cloudflare.
     */
    private void runSyncDns(TaskEntity task, Map<String, Object> config) {
        @SuppressWarnings("unchecked")
        List<String> accountIds = (List<String>) config.getOrDefault("account_ids", List.of());
        log.info("[TaskExecutor] sync_dns: account_ids={}", accountIds);
    }

    /**
     * Sync security rules from Cloudflare for all zones.
     */
    private void runSyncSecurity(TaskEntity task, Map<String, Object> config) {
        @SuppressWarnings("unchecked")
        List<String> accountIds = (List<String>) config.getOrDefault("account_ids", List.of());
        log.info("[TaskExecutor] sync_security: account_ids={}", accountIds);
    }

    /**
     * Sync cache rules from Cloudflare for all zones.
     */
    private void runSyncCache(TaskEntity task, Map<String, Object> config) {
        @SuppressWarnings("unchecked")
        List<String> accountIds = (List<String>) config.getOrDefault("account_ids", List.of());
        log.info("[TaskExecutor] sync_cache: account_ids={}", accountIds);
    }

    /**
     * Sync domain (dns_records -> domain collection): calls domain service.
     */
    private void runSyncDomain(TaskEntity task, Map<String, Object> config) {
        log.info("[TaskExecutor] sync_domain: POST /domain/domain/sync");
    }

    /**
     * Push sync rules to Cloudflare.
     */
    private void runSyncRule(TaskEntity task, Map<String, Object> config) {
        @SuppressWarnings("unchecked")
        List<Integer> syncRuleIds = (List<Integer>) config.getOrDefault("sync_rule_ids", List.of());
        log.info("[TaskExecutor] sync_rule: sync_rule_ids={}", syncRuleIds);
    }

    /**
     * Sync project domains: triggers check on sync domain rules.
     * In Python: GET /sync_domain/rules → for each enabled rule → POST /sync_domain/rules/{id}/check
     */
    private void runSyncProjectDomain(TaskEntity task, Map<String, Object> config) {
        @SuppressWarnings("unchecked")
        List<Integer> ruleIds = (List<Integer>) config.getOrDefault("sync_project_domain_rule_ids", List.of());
        log.info("[TaskExecutor] sync_project_domain: rule_ids={}", ruleIds);
    }

    /**
     * Sync statistic (table + chart) for today or a specific date.
     */
    private void runSyncStatistic(TaskEntity task, Map<String, Object> config) {
        String date = (String) config.getOrDefault("date", "");
        String groupId = (String) config.getOrDefault("group_id", "");
        log.info("[TaskExecutor] sync_statistic: date={}, groupId={}", date, groupId);
    }

    /**
     * Sync statistic month (table + chart) for a specific month.
     */
    private void runSyncStatisticMonth(TaskEntity task, Map<String, Object> config) {
        String month = (String) config.getOrDefault("month", "");
        String groupId = (String) config.getOrDefault("group_id", "");
        log.info("[TaskExecutor] sync_statistic_month: month={}, groupId={}", month, groupId);
    }

    // ======================== Helpers ========================

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfig(String configJson) {
        if (configJson == null || configJson.isBlank()) return new HashMap<>();
        try {
            return objectMapper.readValue(configJson, Map.class);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private void updateTaskStatus(Long taskId, String status) {
        try {
            var task = taskMapper.selectById(taskId);
            if (task != null) {
                task.setLastStatus(status);
                task.setLastRunAt(new Date());
                task.setUpdatedAt(new Date());
                taskMapper.updateById(task);
            }
        } catch (Exception e) {
            log.warn("[TaskExecutor] Failed to update status for task {}: {}", taskId, e.getMessage());
        }
    }
}