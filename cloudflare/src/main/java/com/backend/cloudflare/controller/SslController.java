package com.backend.cloudflare.controller;

import com.backend.utils.dto.ApiResponseDto;
import com.backend.cloudflare.service.CfClientService;
import com.backend.cloudflare.service.MongoDbService;
import com.backend.cloudflare.mapper.AccountMapper;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

/**
 * SSL 管理 Controller
 * 对应 Python 的 routes/ssl.py
 */
@Slf4j
@RestController
@RequestMapping("")
public class SslController {

    @Autowired
    private CfClientService cfClient;

    @Autowired
    private MongoDbService mongoDbService;

    @Autowired
    private AccountMapper accountMapper;

    @GetMapping("/ssl")
    public ResponseEntity<ApiResponseDto<List<Map<String, Object>>>> listAllSsl(@RequestParam Long accountId) {
        List<Map<String, Object>> allSettings = new ArrayList<>();
        String prefix = "account_" + accountId + "_zone_";
        for (String collName : mongoDbService.listCollectionNames()) {
            if (collName.startsWith(prefix) && collName.endsWith("_ssl")) {
                for (Document doc : mongoDbService.getCollection(collName)
                        .find(new Document("account_id", String.valueOf(accountId)))) {
                    allSettings.add(docToMap(doc));
                }
            }
        }
        return ResponseEntity.ok(ApiResponseDto.success("ok", allSettings));
    }

    @PostMapping("/zone/{zoneId}/ssl/sync")
    public ResponseEntity<ApiResponseDto<Object>> syncSsl(
            @RequestParam Long accountId,
            @PathVariable String zoneId,
            @RequestHeader("X-Cf-Token") String cfToken) {
        log.info("[SSL Sync] Start sync for account_id={}, zone_id={}", accountId, zoneId);

        var account = accountMapper.selectById(accountId);
        if (account == null) {
            return ResponseEntity.status(404).body(ApiResponseDto.error("Account not found"));
        }

        Map<String, Object> cfData = cfClient.getSslSetting(cfToken, zoneId);
        if (!cfData.containsKey("success") || Boolean.FALSE.equals(cfData.get("success"))) {
            return ResponseEntity.status(500).body(ApiResponseDto.error("Failed to fetch from Cloudflare"));
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) cfData.get("result");
        String now = Instant.now().toString();
        String collName = mongoDbService.getZoneCollectionName(accountId, zoneId, "ssl");

        Document doc = new Document()
                .append("zone_id", zoneId)
                .append("account_id", String.valueOf(accountId))
                .append("ssl_mode", result.getOrDefault("value", "off"))
                .append("editable", Boolean.TRUE.equals(result.get("editable")))
                .append("modified_on", result.getOrDefault("modified_on", ""))
                .append("synced_at", now);

        mongoDbService.upsert(collName,
                new Document("zone_id", zoneId).append("account_id", String.valueOf(accountId)),
                doc);

        log.info("[SSL Sync] Complete: ssl_mode={}", doc.getString("ssl_mode"));
        return ResponseEntity.ok(ApiResponseDto.success("ok", Map.of("synced", 1)));
    }

    @GetMapping("/zone/{zoneId}/ssl")
    public ResponseEntity<ApiResponseDto<List<Map<String, Object>>>> listSsl(
            @RequestParam Long accountId,
            @PathVariable String zoneId) {
        String collName = mongoDbService.getZoneCollectionName(accountId, zoneId, "ssl");
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Document doc : mongoDbService.getCollection(collName)
                .find(new Document("account_id", String.valueOf(accountId)))) {
            rows.add(docToMap(doc));
        }
        return ResponseEntity.ok(ApiResponseDto.success("ok", rows));
    }

    @PatchMapping("/zone/{zoneId}/ssl")
    public ResponseEntity<ApiResponseDto<Object>> updateSsl(
            @RequestParam Long accountId,
            @PathVariable String zoneId,
            @RequestHeader("X-Cf-Token") String cfToken,
            @RequestBody Map<String, String> body) {
        String value = body.getOrDefault("value", "full");
        cfClient.updateSslSetting(cfToken, zoneId, Map.of("value", value));

        String now = Instant.now().toString();
        String collName = mongoDbService.getZoneCollectionName(accountId, zoneId, "ssl");
        mongoDbService.upsert(collName,
                new Document("zone_id", zoneId).append("account_id", String.valueOf(accountId)),
                new Document("ssl_mode", value).append("synced_at", now));

        return ResponseEntity.ok(ApiResponseDto.success("ok", Map.of("ssl_mode", value)));
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