package com.backend.bot.controller;

import com.backend.bot.service.WhitelistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import network.HttpResponseUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/whitelist")
public class WhitelistManagementController {

    private final WhitelistService whitelistService;

    @GetMapping("/ips")
    public Mono<ResponseEntity<Map<String, Object>>> getWhitelistIps(
            @RequestParam(required = false) String domainType) {
        return whitelistService.getWhitelistIps(domainType)
                .map(ips -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("ips", ips);
                    data.put("domainType", domainType);
                    data.put("total", ips.size());
                    return HttpResponseUtils.ok(data);
                });
    }

    @PostMapping("/ips")
    public Mono<ResponseEntity<Map<String, Object>>> addIpToWhitelist(
            @RequestParam String ip,
            @RequestParam String username,
            @RequestParam String domainType) {
        return whitelistService.addIpToWhitelist(ip, username, domainType)
                .map(success -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("ip", ip);
                    data.put("username", username);
                    data.put("domainType", domainType);
                    data.put("timestamp", System.currentTimeMillis());
                    if (Boolean.TRUE.equals(success)) {
                        data.put("requestId", UUID.randomUUID().toString());
                        return HttpResponseUtils.ok(data);
                    } else {
                        return HttpResponseUtils.internalError("Failed to add IP to whitelist");
                    }
                })
                .onErrorResume(e -> {
                    log.error("❌ Add IP to whitelist failed", e);
                    return Mono.just(HttpResponseUtils.internalError("Operation failed: " + e.getMessage()));
                });
    }

    @DeleteMapping("/ips/{ip}")
    public Mono<ResponseEntity<Map<String, Object>>> deleteIpFromWhitelist(
            @PathVariable String ip,
            @RequestParam String domainType) {
        return whitelistService.removeIpFromWhitelist(ip, domainType)
                .map(removed -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("ip", ip);
                    data.put("domainType", domainType);
                    if (Boolean.TRUE.equals(removed)) {
                        return HttpResponseUtils.ok(data);
                    } else {
                        return HttpResponseUtils.notFound("IP not found in whitelist: " + ip);
                    }
                })
                .onErrorResume(e -> {
                    log.error("❌ Delete IP from whitelist failed", e);
                    return Mono.just(HttpResponseUtils.internalError("Operation failed: " + e.getMessage()));
                });
    }

    @GetMapping("/audit")
    public Mono<ResponseEntity<Map<String, Object>>> getAuditLog(
            @RequestParam(defaultValue = "50") Integer limit,
            @RequestParam(defaultValue = "0") Integer offset) {
        // TODO: 需要 MongoDB 存储审计日志，暂时返回空
        Map<String, Object> data = new HashMap<>();
        data.put("auditLog", new Object[0]);
        data.put("pagination", Map.of("limit", limit, "offset", offset, "total", 0));
        return Mono.just(HttpResponseUtils.ok(data));
    }

    @PostMapping("/ips/batch")
    public Mono<ResponseEntity<Map<String, Object>>> batchAddIpToWhitelist(
            @RequestBody BatchAddRequest request) {
        if (request.getItems() == null || request.getItems().length == 0) {
            return Mono.just(HttpResponseUtils.badRequest("No items provided"));
        }
        return reactor.core.publisher.Flux.fromArray(request.getItems())
                .flatMap(item -> whitelistService.addIpToWhitelist(
                        item.getIp(), request.getUsername(), request.getDomainType()))
                .reduce(new int[]{0, 0}, (acc, success) -> {
                    if (Boolean.TRUE.equals(success)) acc[0]++;
                    else acc[1]++;
                    return acc;
                })
                .map(counts -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("batchId", UUID.randomUUID().toString());
                    data.put("totalRequested", request.getItems().length);
                    data.put("successCount", counts[0]);
                    data.put("failureCount", counts[1]);
                    return HttpResponseUtils.ok(data);
                });
    }

    public static class BatchAddRequest {
        private String domainType;
        private String username;
        private BatchAddItem[] items;

        public String getDomainType() { return domainType; }
        public void setDomainType(String domainType) { this.domainType = domainType; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public BatchAddItem[] getItems() { return items; }
        public void setItems(BatchAddItem[] items) { this.items = items; }
    }

    public static class BatchAddItem {
        private String ip;
        public String getIp() { return ip; }
        public void setIp(String ip) { this.ip = ip; }
    }
}
