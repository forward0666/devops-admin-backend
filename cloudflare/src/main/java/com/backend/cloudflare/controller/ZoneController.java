package com.backend.cloudflare.controller;

import com.backend.utils.dto.ApiResponseDto;
import com.backend.cloudflare.service.CfClientService;
import com.backend.cloudflare.service.MongoDbService;
import com.backend.cloudflare.mapper.AccountMapper;
import com.backend.cloudflare.entity.AccountEntity;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Zone 管理 Controller
 * 对应 Python 的 routes/zones.py
 */
@Slf4j
@RestController
@RequestMapping("")
public class ZoneController {

    @Autowired
    private CfClientService cfClient;

    @Autowired
    private MongoDbService mongoDbService;

    @Autowired
    private AccountMapper accountMapper;

    @PostMapping("/zone/sync")
    public ResponseEntity<ApiResponseDto<Object>> syncZones(
            @RequestParam Long accountId,
            @RequestHeader("X-Cf-Token") String cfToken) {
        log.info("[Zone Sync] Start sync for account_id={}", accountId);

        AccountEntity account = accountMapper.findById(accountId);
        if (account == null) {
            return ResponseEntity.status(404).body(ApiResponseDto.error("Account not found"));
        }

        // Verify token
        try {
            Map<String, Object> verifyData = cfClient.verifyToken(cfToken);
            if (verifyData != null && Boolean.TRUE.equals(verifyData.get("success"))) {
                log.info("[Zone Sync] Token valid");
            }
        } catch (Exception e) {
            log.warn("[Zone Sync] Token verify error (non-fatal): {}", e.getMessage());
        }

        // Fetch zones
        @SuppressWarnings("unchecked")
        Map<String, Object> cfData;
        try {
            cfData = cfClient.listZones(cfToken, 50, null);
        } catch (Exception e) {
            log.error("[Zone Sync] CF API error: {}", e.getMessage());
            return ResponseEntity.ok(ApiResponseDto.success("ok",
                    Map.of("code", 403, "message", "CF API error: " + e.getMessage())));
        }

        if (!Boolean.TRUE.equals(cfData.get("success"))) {
            return ResponseEntity.status(500).body(ApiResponseDto.error("Failed to fetch from Cloudflare"));
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> zones = (List<Map<String, Object>>) cfData.get("result");
        if (zones == null || zones.isEmpty()) {
            return ResponseEntity.ok(ApiResponseDto.success("ok",
                    Map.of("synced", 0, "skipped", 0, "total", 0)));
        }

        // Determine CF account ID
        String savedCfAccountId = account.getCfAccountId() != null ? account.getCfAccountId() : "";
        Map<String, Integer> accountCounts = new HashMap<>();
        for (Map<String, Object> z : zones) {
            @SuppressWarnings("unchecked")
            Map<String, Object> zAccount = (Map<String, Object>) z.get("account");
            String cfAcc = zAccount != null ? String.valueOf(zAccount.getOrDefault("id", "")) : "";
            if (!cfAcc.isEmpty()) {
                accountCounts.merge(cfAcc, 1, Integer::sum);
            }
        }
        String inferredCfAccountId = accountCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("");
        String cfAccountId = !savedCfAccountId.isEmpty() ? savedCfAccountId : inferredCfAccountId;
        log.info("[Zone Sync] CF account: saved={}, inferred={}, using={}", savedCfAccountId, inferredCfAccountId, cfAccountId);

        // Save cf_account_id if first sync
        if (savedCfAccountId.isEmpty() && !inferredCfAccountId.isEmpty()) {
            try {
                account.setCfAccountId(inferredCfAccountId);
                accountMapper.update(account);
                log.info("[Zone Sync] Saved cf_account_id={}", inferredCfAccountId);
            } catch (Exception e) {
                log.warn("[Zone Sync] Failed to save cf_account_id: {}", e.getMessage());
            }
        }

        // Clear old data
        String collName = mongoDbService.getCollectionName(accountId, "zones");
        mongoDbService.getCollection(collName).deleteMany(new Document("account_id", String.valueOf(accountId)));

        // Write zones
        String now = Instant.now().toString();
        int synced = 0, skipped = 0;
        String finalCfAccountId = cfAccountId;
        for (Map<String, Object> zone : zones) {
            @SuppressWarnings("unchecked")
            Map<String, Object> zoneAccount = (Map<String, Object>) zone.get("account");
            String zoneCfAccount = zoneAccount != null ? String.valueOf(zoneAccount.getOrDefault("id", "")) : "";
            if (!finalCfAccountId.isEmpty() && !zoneCfAccount.equals(finalCfAccountId)) {
                skipped++;
                continue;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> zonePlan = (Map<String, Object>) zone.get("plan");
            @SuppressWarnings("unchecked")
            List<String> nameServers = (List<String>) zone.get("name_servers");

            Document doc = new Document()
                    .append("zone_id", zone.get("id"))
                    .append("account_id", String.valueOf(accountId))
                    .append("account_name", account.getName())
                    .append("name", String.valueOf(zone.getOrDefault("name", "")))
                    .append("status", String.valueOf(zone.getOrDefault("status", "")))
                    .append("paused", Boolean.TRUE.equals(zone.get("paused")))
                    .append("plan", zonePlan != null ? String.valueOf(zonePlan.getOrDefault("name", "")) : "")
                    .append("name_servers", nameServers != null ? nameServers.toString() : "[]")
                    .append("ssl_mode", "")
                    .append("synced_at", now);

            mongoDbService.upsert(collName,
                    new Document("zone_id", zone.get("id"))
                            .append("account_id", String.valueOf(accountId)),
                    doc);
            synced++;
        }

        log.info("[Zone Sync] Complete for account_id={}: synced={}, skipped={}", accountId, synced, skipped);
        return ResponseEntity.ok(ApiResponseDto.success("ok",
                Map.of("synced", synced, "skipped", skipped, "total", zones.size())));
    }

    @PostMapping("/zone/syncAll")
    public ResponseEntity<ApiResponseDto<List<Map<String, Object>>>> syncAllZones() {
        List<AccountEntity> accounts = accountMapper.findAll();
        if (accounts.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponseDto.error("No accounts found"));
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (AccountEntity acc : accounts) {
            try {
                String token = acc.getApiKey();
                if (token == null || token.isEmpty()) continue;
                ResponseEntity<ApiResponseDto<Object>> result = syncZones(acc.getId(), token);
                results.add(Map.of("account_id", acc.getId(), "account_name", acc.getName(),
                        "data", result.getBody() != null ? result.getBody().getData() : null));
            } catch (Exception e) {
                log.error("[Zone Sync All] Failed for account {}: {}", acc.getId(), e.getMessage());
                results.add(Map.of("account_id", acc.getId(), "account_name", acc.getName(), "error", e.getMessage()));
            }
        }
        return ResponseEntity.ok(ApiResponseDto.success("ok", results));
    }

    @GetMapping("/zone")
    public ResponseEntity<ApiResponseDto<List<Map<String, Object>>>> listZones(
            @RequestParam(required = false) Long accountId) {
        List<Map<String, Object>> rows = new ArrayList<>();

        if (accountId != null) {
            String collName = mongoDbService.getCollectionName(accountId, "zones");
            for (Document doc : mongoDbService.getCollection(collName).find().sort(new Document("name", 1))) {
                rows.add(docToMap(doc));
            }
        } else {
            for (String colName : mongoDbService.listCollectionNames()) {
                if (colName.endsWith("_zones")) {
                    for (Document doc : mongoDbService.getCollection(colName).find().sort(new Document("name", 1))) {
                        rows.add(docToMap(doc));
                    }
                }
            }
        }
        return ResponseEntity.ok(ApiResponseDto.success("ok", rows));
    }

    @DeleteMapping("/zone/sync")
    public ResponseEntity<ApiResponseDto<Object>> clearZones(@RequestParam Long accountId) {
        String collName = mongoDbService.getCollectionName(accountId, "zones");
        long deleted = mongoDbService.getCollection(collName)
                .deleteMany(new Document("account_id", String.valueOf(accountId))).getDeletedCount();
        return ResponseEntity.ok(ApiResponseDto.success("ok", Map.of("deleted", deleted)));
    }

    @GetMapping("/token/verify")
    public ResponseEntity<ApiResponseDto<Object>> verifyToken(
            @RequestHeader("X-Cf-Token") String cfToken) {
        Map<String, Object> result = cfClient.verifyToken(cfToken);
        boolean success = Boolean.TRUE.equals(result.get("success"));
        return ResponseEntity.ok(ApiResponseDto.success("ok",
                Map.of("status", success ? "active" : "invalid")));
    }

    @GetMapping("/account/cf/list")
    public ResponseEntity<ApiResponseDto<List<Map<String, Object>>>> listCfAccounts() {
        List<AccountEntity> accounts = accountMapper.findAll();
        List<Map<String, Object>> result = new ArrayList<>();
        for (AccountEntity acc : accounts) {
            result.add(Map.of(
                    "id", acc.getId(),
                    "name", acc.getName(),
                    "cf_account_id", acc.getCfAccountId() != null ? acc.getCfAccountId() : ""
            ));
        }
        return ResponseEntity.ok(ApiResponseDto.success("ok", result));
    }

    private Map<String, Object> docToMap(Document doc) {
        Map<String, Object> map = new HashMap<>();
        for (String key : doc.keySet()) {
            Object val = doc.get(key);
            if (val instanceof org.bson.types.ObjectId) {
                map.put(key, val.toString());
            } else {
                map.put(key, val);
            }
        }
        return map;
    }
}