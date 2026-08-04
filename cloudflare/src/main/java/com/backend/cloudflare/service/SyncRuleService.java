package com.backend.cloudflare.service;

import com.backend.cloudflare.entity.AccountEntity;
import com.backend.cloudflare.entity.CfSyncRuleEntity;
import com.backend.cloudflare.mapper.AccountMapper;
import com.backend.cloudflare.mapper.CfSyncRuleMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SyncRuleService {

    private final CfSyncRuleMapper syncRuleMapper;
    private final AccountMapper accountMapper;
    private final CfClientService cfClientService;
    private final ObjectMapper objectMapper;

    // ---- CRUD ----

    /**
     * List all sync rules.
     */
    public List<CfSyncRuleEntity> listAll() {
        return syncRuleMapper.selectList(null);
    }

    /**
     * List sync rules for a specific account.
     */
    public List<CfSyncRuleEntity> listByAccount(Long accountId) {
        return syncRuleMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CfSyncRuleEntity>()
                        .eq(CfSyncRuleEntity::getAccountId, accountId)
        );
    }

    /**
     * Get a sync rule by ID.
     */
    public CfSyncRuleEntity getById(Long id) {
        return syncRuleMapper.selectById(id);
    }

    /**
     * Create a new sync rule.
     */
    public CfSyncRuleEntity create(CfSyncRuleEntity entity) {
        entity.setEnabled(entity.getEnabled() != null && entity.getEnabled());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        syncRuleMapper.insert(entity);
        log.info("Created sync rule: id={}, name={}", entity.getId(), entity.getName());
        return entity;
    }

    /**
     * Update an existing sync rule.
     */
    public CfSyncRuleEntity update(CfSyncRuleEntity entity) {
        CfSyncRuleEntity existing = syncRuleMapper.selectById(entity.getId());
        if (existing == null) {
            throw new RuntimeException("Sync rule not found: " + entity.getId());
        }
        entity.setUpdatedAt(LocalDateTime.now());
        syncRuleMapper.updateById(entity);
        log.info("Updated sync rule: id={}", entity.getId());
        return syncRuleMapper.selectById(entity.getId());
    }

    /**
     * Delete a sync rule by ID.
     */
    public void delete(Long id) {
        CfSyncRuleEntity existing = syncRuleMapper.selectById(id);
        if (existing == null) {
            throw new RuntimeException("Sync rule not found: " + id);
        }
        syncRuleMapper.deleteById(id);
        log.info("Deleted sync rule: id={}", id);
    }

    // ---- Push Sync ----

    /**
     * Execute a sync rule: push DNS records / settings from source to target zones.
     *
     * @param ruleId the sync rule ID to execute
     * @return summary of what was synced
     */
    public Map<String, Object> executeSync(Long ruleId) {
        CfSyncRuleEntity rule = syncRuleMapper.selectById(ruleId);
        if (rule == null) {
            throw new RuntimeException("Sync rule not found: " + ruleId);
        }
        if (!Boolean.TRUE.equals(rule.getEnabled())) {
            throw new RuntimeException("Sync rule " + ruleId + " is disabled");
        }

        AccountEntity account = accountMapper.selectById(rule.getAccountId());
        if (account == null || account.getApiKey() == null || account.getApiKey().isBlank()) {
            throw new RuntimeException("Account not found or no API key for rule " + ruleId);
        }

        String apiToken = account.getApiKey();
        List<String> targetZoneIds = parseJsonList(rule.getTargetZoneIds());
        List<String> ruleTypes = parseJsonList(rule.getRuleTypes());

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("ruleId", ruleId);
        summary.put("ruleName", rule.getName());
        summary.put("sourceZoneId", rule.getSourceZoneId());
        summary.put("targetZoneIds", targetZoneIds);
        summary.put("ruleTypes", ruleTypes);
        summary.put("status", "started");

        List<Map<String, Object>> results = new ArrayList<>();

        // Read source zone data for the requested rule types
        Map<String, Object> sourceData = fetchSourceData(apiToken, rule.getSourceZoneId(), ruleTypes);

        // Push to each target zone
        for (String targetZoneId : targetZoneIds) {
            Map<String, Object> targetResult = new LinkedHashMap<>();
            targetResult.put("targetZoneId", targetZoneId);
            try {
                pushToZone(apiToken, rule.getSourceZoneId(), targetZoneId, ruleTypes, sourceData);
                targetResult.put("status", "success");
            } catch (Exception e) {
                log.error("Failed to sync rule {} to zone {}: {}", ruleId, targetZoneId, e.getMessage());
                targetResult.put("status", "error");
                targetResult.put("message", e.getMessage());
            }
            results.add(targetResult);
        }

        // Update last synced time
        rule.setLastSyncedAt(LocalDateTime.now());
        syncRuleMapper.updateById(rule);

        summary.put("results", results);
        summary.put("status", "completed");
        summary.put("syncedAt", rule.getLastSyncedAt().toString());
        return summary;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchSourceData(String apiToken, String sourceZoneId, List<String> ruleTypes) {
        Map<String, Object> data = new LinkedHashMap<>();

        for (String ruleType : ruleTypes) {
            switch (ruleType.toLowerCase()) {
                case "dns_records":
                case "dns":
                    data.put("dns_records", cfClientService.listDnsRecords(apiToken, sourceZoneId, 50));
                    break;
                case "settings":
                    data.put("settings", cfClientService.getZoneSettings(apiToken, sourceZoneId));
                    break;
                case "ssl":
                    data.put("ssl", cfClientService.getSslSetting(apiToken, sourceZoneId));
                    break;
                case "rulesets":
                    data.put("rulesets", listRulesetsWithDetails(apiToken, sourceZoneId));
                    break;
                default:
                    log.warn("Unknown rule type: {}", ruleType);
            }
        }

        return data;
    }

    @SuppressWarnings("unchecked")
    private void pushToZone(String apiToken, String sourceZoneId, String targetZoneId,
                            List<String> ruleTypes, Map<String, Object> sourceData) {
        for (String ruleType : ruleTypes) {
            switch (ruleType.toLowerCase()) {
                case "dns_records":
                case "dns":
                    pushDnsRecords(apiToken, sourceZoneId, targetZoneId, sourceData);
                    break;
                case "settings":
                    pushSettings(apiToken, targetZoneId, sourceData);
                    break;
                case "ssl":
                    pushSsl(apiToken, targetZoneId, sourceData);
                    break;
                case "rulesets":
                    pushRulesets(apiToken, sourceZoneId, targetZoneId, sourceData);
                    break;
                default:
                    log.warn("Unknown rule type: {}", ruleType);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void pushDnsRecords(String apiToken, String sourceZoneId, String targetZoneId,
                                Map<String, Object> sourceData) {
        Map<String, Object> dnsData = (Map<String, Object>) sourceData.get("dns_records");
        if (dnsData == null) return;

        List<Map<String, Object>> records = (List<Map<String, Object>>) dnsData.get("result");
        if (records == null || records.isEmpty()) return;

        // Get existing records in target zone
        Map<String, Object> targetDnsResponse = cfClientService.listDnsRecords(apiToken, targetZoneId, 50);
        List<Map<String, Object>> existingRecords = (List<Map<String, Object>>) targetDnsResponse.getOrDefault("result", List.of());

        // Index existing records by (type, name) for matching
        Map<String, Map<String, Object>> existingIndex = new LinkedHashMap<>();
        for (Map<String, Object> rec : existingRecords) {
            String key = rec.get("type") + ":" + rec.get("name");
            existingIndex.put(key, rec);
        }

        for (Map<String, Object> record : records) {
            String type = (String) record.get("type");
            String name = (String) record.get("name");
            String content = (String) record.get("content");
            Integer ttl = (Integer) record.get("ttl");
            Boolean proxied = (Boolean) record.get("proxied");

            // Skip CNAME/ALIAS etc. if pointing to the source zone itself — avoid circular
            if (name != null && name.contains(sourceZoneId)) continue;

            Map<String, Object> newRec = new LinkedHashMap<>();
            newRec.put("type", type);
            newRec.put("name", name);
            newRec.put("content", content);
            newRec.put("ttl", ttl != null ? ttl : 1);
            newRec.put("proxied", proxied != null ? proxied : false);

            String key = type + ":" + name;
            if (existingIndex.containsKey(key)) {
                // Update existing
                String existingId = (String) existingIndex.get(key).get("id");
                cfClientService.updateDnsRecord(apiToken, targetZoneId, existingId, newRec);
            } else {
                // Create new
                cfClientService.createDnsRecord(apiToken, targetZoneId, newRec);
            }
        }

        log.info("Pushed {} DNS records from zone {} to zone {}", records.size(), sourceZoneId, targetZoneId);
    }

    @SuppressWarnings("unchecked")
    private void pushSettings(String apiToken, String targetZoneId, Map<String, Object> sourceData) {
        Map<String, Object> settingsData = (Map<String, Object>) sourceData.get("settings");
        if (settingsData == null) return;

        List<Map<String, Object>> results = (List<Map<String, Object>>) settingsData.get("result");
        if (results == null || results.isEmpty()) return;

        Map<String, Object> settingsMap = new LinkedHashMap<>();
        for (Map<String, Object> setting : results) {
            String id = (String) setting.get("id");
            Object value = setting.get("value");
            if (id != null && value != null) {
                settingsMap.put(id, Map.of("id", id, "value", value));
            }
        }

        if (!settingsMap.isEmpty()) {
            cfClientService.updateZoneSettings(apiToken, targetZoneId, settingsMap);
            log.info("Pushed {} settings to zone {}", settingsMap.size(), targetZoneId);
        }
    }

    @SuppressWarnings("unchecked")
    private void pushSsl(String apiToken, String targetZoneId, Map<String, Object> sourceData) {
        Map<String, Object> sslData = (Map<String, Object>) sourceData.get("ssl");
        if (sslData == null) return;

        Map<String, Object> result = (Map<String, Object>) sslData.get("result");
        if (result == null) return;

        Map<String, Object> sslValue = new LinkedHashMap<>();
        sslValue.put("value", result.get("value"));

        cfClientService.updateSslSetting(apiToken, targetZoneId, sslValue);
        log.info("Pushed SSL setting to zone {}", targetZoneId);
    }

    @SuppressWarnings("unchecked")
    private void pushRulesets(String apiToken, String sourceZoneId, String targetZoneId,
                              Map<String, Object> sourceData) {
        // Rulesets sync is complex — minimal implementation:
        // read source rulesets and create/update matching ones on target
        List<Map<String, Object>> sourceRulesets = (List<Map<String, Object>>) sourceData.get("rulesets");
        if (sourceRulesets == null || sourceRulesets.isEmpty()) return;

        // Get target rulesets
        Map<String, Object> targetRulesetsResp = cfClientService.listRulesets(apiToken, targetZoneId);
        List<Map<String, Object>> targetRulesets = (List<Map<String, Object>>) targetRulesetsResp.getOrDefault("result", List.of());
        Set<String> targetRulesetNames = targetRulesets.stream()
                .map(r -> (String) r.get("name"))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (Map<String, Object> ruleset : sourceRulesets) {
            String name = (String) ruleset.get("name");
            if (name == null) continue;

            // Skip if already exists on target
            if (targetRulesetNames.contains(name)) continue;

            // Create a simplified copy
            Map<String, Object> newRuleset = new LinkedHashMap<>();
            newRuleset.put("name", name);
            newRuleset.put("description", ruleset.get("description"));
            newRuleset.put("kind", ruleset.get("kind"));
            newRuleset.put("phase", ruleset.get("phase"));
            newRuleset.put("rules", ruleset.get("rules"));

            cfClientService.createRuleset(apiToken, targetZoneId, newRuleset);
            log.info("Pushed ruleset '{}' to zone {}", name, targetZoneId);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listRulesetsWithDetails(String apiToken, String zoneId) {
        Map<String, Object> resp = cfClientService.listRulesets(apiToken, zoneId);
        List<Map<String, Object>> rulesets = (List<Map<String, Object>>) resp.getOrDefault("result", List.of());
        // Fetch details for each ruleset (includes rules)
        List<Map<String, Object>> detailed = new ArrayList<>();
        for (Map<String, Object> rs : rulesets) {
            String rsId = (String) rs.get("id");
            if (rsId != null) {
                Map<String, Object> detail = cfClientService.getRuleset(apiToken, zoneId, rsId);
                Map<String, Object> detailResult = (Map<String, Object>) detail.get("result");
                if (detailResult != null) {
                    detailed.add(detailResult);
                } else {
                    detailed.add(rs);
                }
            } else {
                detailed.add(rs);
            }
        }
        return detailed;
    }

    // ---- Helpers ----

    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse JSON list: {}", json, e);
            return List.of();
        }
    }
}