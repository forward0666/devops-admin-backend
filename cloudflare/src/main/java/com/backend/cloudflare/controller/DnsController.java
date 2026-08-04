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
 * DNS 管理 Controller
 * 对应 Python 的 routes/dns.py
 */
@Slf4j
@RestController
@RequestMapping("")
public class DnsController {

    @Autowired
    private CfClientService cfClient;

    @Autowired
    private MongoDbService mongoDbService;

    @Autowired
    private AccountMapper accountMapper;

    @PostMapping("/dns/sync")
    public ResponseEntity<ApiResponseDto<Object>> syncDns(
            @RequestParam Long accountId,
            @RequestHeader("X-Cf-Token") String cfToken) {
        log.info("[DNS Sync] Start sync for account_id={}", accountId);

        AccountEntity account = accountMapper.findById(accountId);
        if (account == null) {
            return ResponseEntity.status(404).body(ApiResponseDto.error("Account not found"));
        }

        // Get synced zones
        String zonesCollName = mongoDbService.getCollectionName(accountId, "zones");
        List<Document> zones = new ArrayList<>();
        mongoDbService.getCollection(zonesCollName)
                .find(new Document("account_id", String.valueOf(accountId)))
                .into(zones);

        if (zones.isEmpty()) {
            return ResponseEntity.status(404).body(ApiResponseDto.error("No synced zones found. Sync zones first."));
        }

        // Clear old DNS records
        String dnsCollName = mongoDbService.getCollectionName(accountId, "dns_records");
        long deleted = mongoDbService.getCollection(dnsCollName)
                .deleteMany(new Document("account_id", String.valueOf(accountId))).getDeletedCount();
        log.info("[DNS Sync] Cleared {} old records", deleted);

        String now = Instant.now().toString();
        int totalSynced = 0;

        for (Document zone : zones) {
            String zoneId = zone.getString("zone_id");
            String zoneName = zone.getString("name");
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> cfData = cfClient.listDnsRecords(cfToken, zoneId, 100);
                if (cfData == null || !Boolean.TRUE.equals(cfData.get("success"))) continue;

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> records = (List<Map<String, Object>>) cfData.get("result");
                if (records == null || records.isEmpty()) continue;

                int count = 0;
                for (Map<String, Object> r : records) {
                    Document doc = new Document()
                            .append("record_id", String.valueOf(r.getOrDefault("id", "")))
                            .append("zone_id", zoneId)
                            .append("zone_name", zoneName)
                            .append("account_id", String.valueOf(accountId))
                            .append("account_name", account.getName())
                            .append("type", String.valueOf(r.getOrDefault("type", "")))
                            .append("name", String.valueOf(r.getOrDefault("name", "")))
                            .append("content", String.valueOf(r.getOrDefault("content", "")))
                            .append("proxied", Boolean.TRUE.equals(r.get("proxied")))
                            .append("ttl", r.getOrDefault("ttl", 1))
                            .append("priority", r.get("priority"))
                            .append("synced_at", now);

                    mongoDbService.upsert(dnsCollName,
                            new Document("record_id", String.valueOf(r.getOrDefault("id", "")))
                                    .append("account_id", String.valueOf(accountId)),
                            doc);
                    count++;
                }
                totalSynced += count;
                log.info("[DNS Sync] Zone {}: synced {} records", zoneName, count);
            } catch (Exception e) {
                log.error("[DNS Sync] Failed for zone {}: {}", zoneName, e.getMessage());
            }
        }

        log.info("[DNS Sync] Complete for account_id={}: synced={}", accountId, totalSynced);
        return ResponseEntity.ok(ApiResponseDto.success("ok", Map.of("synced", totalSynced)));
    }

    @PostMapping("/dns/syncAll")
    public ResponseEntity<ApiResponseDto<List<Map<String, Object>>>> syncAllDns() {
        List<AccountEntity> accounts = accountMapper.findAll();
        if (accounts.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponseDto.error("No accounts found"));
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (AccountEntity acc : accounts) {
            try {
                String token = acc.getApiKey();
                if (token == null || token.isEmpty()) continue;
                ResponseEntity<ApiResponseDto<Object>> result = syncDns(acc.getId(), token);
                results.add(Map.of("account_id", acc.getId(), "account_name", acc.getName(),
                        "data", result.getBody() != null ? result.getBody().getData() : null));
            } catch (Exception e) {
                log.error("[DNS Sync All] Failed for account {}: {}", acc.getId(), e.getMessage());
                results.add(Map.of("account_id", acc.getId(), "account_name", acc.getName(), "error", e.getMessage()));
            }
        }
        return ResponseEntity.ok(ApiResponseDto.success("ok", results));
    }

    @GetMapping("/dns")
    public ResponseEntity<ApiResponseDto<List<Map<String, Object>>>> listDns(
            @RequestParam(required = false) Long accountId) {
        List<Map<String, Object>> rows = new ArrayList<>();

        if (accountId != null) {
            String collName = mongoDbService.getCollectionName(accountId, "dns_records");
            for (Document doc : mongoDbService.getCollection(collName)
                    .find(new Document("account_id", String.valueOf(accountId)))
                    .sort(new Document("name", 1))) {
                rows.add(docToMap(doc));
            }
        } else {
            for (String colName : mongoDbService.listCollectionNames()) {
                if (colName.endsWith("_dns_records")) {
                    for (Document doc : mongoDbService.getCollection(colName).find().sort(new Document("name", 1))) {
                        rows.add(docToMap(doc));
                    }
                }
            }
        }
        return ResponseEntity.ok(ApiResponseDto.success("ok", rows));
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