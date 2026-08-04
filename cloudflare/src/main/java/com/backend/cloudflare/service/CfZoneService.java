package com.backend.cloudflare.service;

import com.backend.cloudflare.entity.AccountEntity;
import com.backend.cloudflare.entity.CfZoneEntity;
import com.backend.cloudflare.config.CfApiConfig;
import com.backend.cloudflare.mapper.AccountMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CfZoneService {

    private final AccountMapper accountMapper;
    private final CfClientService cfClientService;
    private final MongoDbService mongoDbService;
    private final CfApiConfig cfApiConfig;
    @Qualifier("cloudflareMongoTemplate")
    private final MongoTemplate mongoTemplate;

    private String zoneCollectionName(Long accountId) {
        return "account_" + accountId + "_zones";
    }

    // ======================== Zone Management ========================

    public void syncZones(Long accountId, String apiToken) {
        log.info("Syncing zones for account {} into collection {}", accountId, zoneCollectionName(accountId));

        String cfAccountId = null;
        AccountEntity account = accountMapper.selectById(accountId);
        if (account != null && account.getCfAccountId() != null && !account.getCfAccountId().isBlank()) {
            cfAccountId = account.getCfAccountId();
        }

        Map<String, Object> response = cfClientService.listZones(apiToken, 50, cfAccountId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawZones = (List<Map<String, Object>>) response.getOrDefault("result", List.of());

        List<CfZoneEntity> entities = rawZones.stream()
                .map(raw -> mapToEntity(raw, accountId, account != null ? account.getName() : null))
                .collect(Collectors.toList());

        String collectionName = zoneCollectionName(accountId);
        mongoTemplate.dropCollection(collectionName);
        if (!entities.isEmpty()) {
            mongoTemplate.insert(entities, collectionName);
        }
        log.info("Synced {} zones for account {} into collection {}", entities.size(), accountId, collectionName);
    }

    public void syncAllZones() {
        List<AccountEntity> accounts = accountMapper.selectList(null);
        for (AccountEntity account : accounts) {
            try {
                if (account.getApiKey() != null && !account.getApiKey().isBlank()) {
                    syncZones(account.getId(), account.getApiKey());
                }
            } catch (Exception e) {
                log.error("Failed to sync zones for account {}: {}", account.getId(), e.getMessage());
            }
        }
    }

    public List<CfZoneEntity> listZones(Long accountId) {
        String collectionName = zoneCollectionName(accountId);
        return mongoTemplate.findAll(CfZoneEntity.class, collectionName);
    }

    public void clearZones(Long accountId) {
        String collectionName = zoneCollectionName(accountId);
        mongoTemplate.dropCollection(collectionName);
    }

    // ======================== Zone Settings ========================

    public Map<String, Object> getZoneSettings(Long accountId, String zoneId) {
        AccountEntity account = accountMapper.selectById(accountId);
        if (account == null || account.getApiKey() == null) {
            throw new RuntimeException("Account not found or no API key: " + accountId);
        }
        return cfClientService.getZoneSettings(account.getApiKey(), zoneId);
    }

    public Map<String, Object> updateZoneSettings(Long accountId, String zoneId, Map<String, Object> settings) {
        AccountEntity account = accountMapper.selectById(accountId);
        if (account == null || account.getApiKey() == null) {
            throw new RuntimeException("Account not found or no API key: " + accountId);
        }
        return cfClientService.updateZoneSettings(account.getApiKey(), zoneId, settings);
    }

    // ======================== Ruleset CRUD (via RulesetHelper) ========================

    /**
     * Ruleset descriptor used to drive the generic ruleset CRUD methods.
     */
    private record RulesetConfig(String name, String phase, String defaultRulesetName) {}

    private static final List<RulesetConfig> RULESET_TYPES = List.of(
        new RulesetConfig("Security", "http_request_firewall_custom", "Default Security Rules"),
        new RulesetConfig("RateLimit", "http_ratelimit", "Default Rate Limiting Rules"),
        new RulesetConfig("DDoS", "ddos_l7", "Default DDoS Rules"),
        new RulesetConfig("Managed", "http_request_firewall_managed", "Default Managed Rules"),
        new RulesetConfig("Cache", "http_request_cache_settings", "Default Cache Rules")
    );

    /**
     * Get API token for a given account. Throws if account not found or has no key.
     */
    private String resolveApiToken(Long accountId) {
        AccountEntity account = accountMapper.selectById(accountId);
        if (account == null || account.getApiKey() == null) {
            throw new RuntimeException("Account not found or no API key: " + accountId);
        }
        return account.getApiKey();
    }

    /**
     * Ensure the ruleset phase entry point exists, returning the ruleset id.
     */
    private String ensureRuleset(String apiToken, String zoneId, RulesetConfig cfg) {
        Map<String, Object> phase = cfClientService.getRulesetPhase(apiToken, zoneId, cfg.phase());
        String rulesetId = (String) phase.get("id");
        if (rulesetId == null) {
            Map<String, Object> newRuleset = new LinkedHashMap<>();
            newRuleset.put("name", cfg.defaultRulesetName());
            newRuleset.put("kind", "zone");
            newRuleset.put("phase", cfg.phase());
            Map<String, Object> created = cfClientService.createRuleset(apiToken, zoneId, newRuleset);
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) created.get("result");
            rulesetId = result != null ? (String) result.get("id") : null;
        }
        if (rulesetId == null) throw new RuntimeException("Failed to get or create " + cfg.name() + " ruleset");
        return rulesetId;
    }

    private String getRulesetId(String apiToken, String zoneId, RulesetConfig cfg) {
        Map<String, Object> phase = cfClientService.getRulesetPhase(apiToken, zoneId, cfg.phase());
        String rulesetId = (String) phase.get("id");
        if (rulesetId == null) throw new RuntimeException(cfg.name() + " ruleset not found for zone: " + zoneId);
        return rulesetId;
    }

    private List<Map<String, Object>> rulesFromPhase(String apiToken, String zoneId, RulesetConfig cfg) {
        Map<String, Object> phase = cfClientService.getRulesetPhase(apiToken, zoneId, cfg.phase());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rules = (List<Map<String, Object>>) phase.getOrDefault("rules", List.of());
        return rules;
    }

    // ======================== Rule CRUD Thin Wrappers ========================

    public List<Map<String, Object>> listSecurityRules(Long accountId, String zoneId) {
        return rulesFromPhase(resolveApiToken(accountId), zoneId, RULESET_TYPES.get(0));
    }
    public Map<String, Object> createSecurityRule(Long accountId, String zoneId, String apiToken, Map<String, Object> body) {
        return cfClientService.createRulesetRule(apiToken, zoneId, ensureRuleset(apiToken, zoneId, RULESET_TYPES.get(0)), body);
    }
    public Map<String, Object> getSecurityRule(Long accountId, String zoneId, String ruleId, String apiToken) {
        return rulesFromPhase(apiToken, zoneId, RULESET_TYPES.get(0)).stream()
            .filter(r -> ruleId.equals(r.get("id"))).findFirst()
            .orElseThrow(() -> new RuntimeException("Security rule not found: " + ruleId));
    }
    public Map<String, Object> updateSecurityRule(Long accountId, String zoneId, String ruleId, String apiToken, Map<String, Object> body) {
        return cfClientService.updateRulesetRule(apiToken, zoneId, getRulesetId(apiToken, zoneId, RULESET_TYPES.get(0)), ruleId, body);
    }
    public Map<String, Object> deleteSecurityRule(Long accountId, String zoneId, String ruleId, String apiToken) {
        return cfClientService.deleteRulesetRule(apiToken, zoneId, getRulesetId(apiToken, zoneId, RULESET_TYPES.get(0)), ruleId);
    }

    public List<Map<String, Object>> listRateLimitRules(Long accountId, String zoneId) {
        return rulesFromPhase(resolveApiToken(accountId), zoneId, RULESET_TYPES.get(1));
    }
    public Map<String, Object> createRateLimitRule(Long accountId, String zoneId, String apiToken, Map<String, Object> body) {
        return cfClientService.createRulesetRule(apiToken, zoneId, ensureRuleset(apiToken, zoneId, RULESET_TYPES.get(1)), body);
    }
    public Map<String, Object> updateRateLimitRule(Long accountId, String zoneId, String ruleId, String apiToken, Map<String, Object> body) {
        return cfClientService.updateRulesetRule(apiToken, zoneId, getRulesetId(apiToken, zoneId, RULESET_TYPES.get(1)), ruleId, body);
    }
    public Map<String, Object> deleteRateLimitRule(Long accountId, String zoneId, String ruleId, String apiToken) {
        return cfClientService.deleteRulesetRule(apiToken, zoneId, getRulesetId(apiToken, zoneId, RULESET_TYPES.get(1)), ruleId);
    }

    public List<Map<String, Object>> listDdosRules(Long accountId, String zoneId) {
        return rulesFromPhase(resolveApiToken(accountId), zoneId, RULESET_TYPES.get(2));
    }
    public Map<String, Object> createDdosRule(Long accountId, String zoneId, String apiToken, Map<String, Object> body) {
        return cfClientService.createRulesetRule(apiToken, zoneId, ensureRuleset(apiToken, zoneId, RULESET_TYPES.get(2)), body);
    }
    public Map<String, Object> updateDdosRule(Long accountId, String zoneId, String ruleId, String apiToken, Map<String, Object> body) {
        return cfClientService.updateRulesetRule(apiToken, zoneId, getRulesetId(apiToken, zoneId, RULESET_TYPES.get(2)), ruleId, body);
    }
    public Map<String, Object> deleteDdosRule(Long accountId, String zoneId, String ruleId, String apiToken) {
        return cfClientService.deleteRulesetRule(apiToken, zoneId, getRulesetId(apiToken, zoneId, RULESET_TYPES.get(2)), ruleId);
    }

    public List<Map<String, Object>> listManagedRules(Long accountId, String zoneId) {
        return rulesFromPhase(resolveApiToken(accountId), zoneId, RULESET_TYPES.get(3));
    }
    public Map<String, Object> createManagedRule(Long accountId, String zoneId, String apiToken, Map<String, Object> body) {
        return cfClientService.createRulesetRule(apiToken, zoneId, ensureRuleset(apiToken, zoneId, RULESET_TYPES.get(3)), body);
    }
    public Map<String, Object> updateManagedRule(Long accountId, String zoneId, String ruleId, String apiToken, Map<String, Object> body) {
        return cfClientService.updateRulesetRule(apiToken, zoneId, getRulesetId(apiToken, zoneId, RULESET_TYPES.get(3)), ruleId, body);
    }
    public Map<String, Object> deleteManagedRule(Long accountId, String zoneId, String ruleId, String apiToken) {
        return cfClientService.deleteRulesetRule(apiToken, zoneId, getRulesetId(apiToken, zoneId, RULESET_TYPES.get(3)), ruleId);
    }

    public List<Map<String, Object>> listCacheRules(Long accountId, String zoneId) {
        return rulesFromPhase(resolveApiToken(accountId), zoneId, RULESET_TYPES.get(4));
    }
    public Map<String, Object> createCacheRule(Long accountId, String zoneId, String apiToken, Map<String, Object> body) {
        return cfClientService.createRulesetRule(apiToken, zoneId, ensureRuleset(apiToken, zoneId, RULESET_TYPES.get(4)), body);
    }
    public Map<String, Object> updateCacheRule(Long accountId, String zoneId, String ruleId, String apiToken, Map<String, Object> body) {
        return cfClientService.updateRulesetRule(apiToken, zoneId, getRulesetId(apiToken, zoneId, RULESET_TYPES.get(4)), ruleId, body);
    }
    public Map<String, Object> deleteCacheRule(Long accountId, String zoneId, String ruleId, String apiToken) {
        return cfClientService.deleteRulesetRule(apiToken, zoneId, getRulesetId(apiToken, zoneId, RULESET_TYPES.get(4)), ruleId);
    }

    // ======================== Cache Purge ========================

    public Map<String, Object> purgeAllCache(Long accountId, String zoneId, String apiToken) {
        Map<String, Object> body = Map.of("purge_everything", true);
        return cfClientService.purgeCache(apiToken, zoneId, body);
    }

    public Map<String, Object> purgeCacheByHosts(Long accountId, String zoneId, String apiToken, List<String> hosts) {
        Map<String, Object> body = Map.of("hosts", hosts);
        return cfClientService.purgeCache(apiToken, zoneId, body);
    }

    public Map<String, Object> purgeCacheByUrls(Long accountId, String zoneId, String apiToken, List<String> urls) {
        Map<String, Object> body = Map.of("files", urls);
        return cfClientService.purgeCache(apiToken, zoneId, body);
    }

    public Map<String, Object> purgeCacheByTags(Long accountId, String zoneId, String apiToken, List<String> tags) {
        Map<String, Object> body = Map.of("tags", tags);
        return cfClientService.purgeCache(apiToken, zoneId, body);
    }

    public Map<String, Object> purgeCacheByPrefixes(Long accountId, String zoneId, String apiToken, List<String> prefixes) {
        Map<String, Object> body = Map.of("prefixes", prefixes);
        return cfClientService.purgeCache(apiToken, zoneId, body);
    }

    // ======================== Analytics ========================

    public Map<String, Object> queryAnalytics(Long accountId, String zoneId, String apiToken, Map<String, Object> query) {
        return cfClientService.analyticsQuery(apiToken, query);
    }

    // ======================== Internal ========================

    @SuppressWarnings("unchecked")
    private CfZoneEntity mapToEntity(Map<String, Object> raw, Long accountId, String accountName) {
        CfZoneEntity entity = new CfZoneEntity();
        entity.setZoneId((String) raw.get("id"));
        entity.setAccountId(String.valueOf(accountId));
        entity.setAccountName(accountName);
        entity.setName((String) raw.get("name"));
        entity.setStatus((String) raw.get("status"));
        entity.setPaused((Boolean) raw.getOrDefault("paused", false));

        Map<String, Object> plan = (Map<String, Object>) raw.get("plan");
        if (plan != null) {
            entity.setPlan((String) plan.get("name"));
        }
        entity.setNameServers((List<String>) raw.get("name_servers"));
        entity.setSyncedAt(LocalDateTime.now());
        return entity;
    }
}