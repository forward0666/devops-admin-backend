package com.backend.cloudflare.controller;

import com.backend.cloudflare.service.CloudflareService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/cf")
@RequiredArgsConstructor
public class CloudflareController {

    private final CloudflareService cloudflareService;

    // ===== Token =====

    @PostMapping("/token/test")
    public Mono<ResponseEntity<JsonNode>> testToken(@RequestBody Map<String, String> body) {
        return cloudflareService.testToken(body.get("apiToken"))
                .map(resp -> ResponseEntity.ok(resp))
                .onErrorResume(e -> Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()));
    }

    // ===== Zones =====

    @GetMapping("/zones")
    public Mono<ResponseEntity<JsonNode>> listZones(@RequestHeader("X-Cf-Token") String apiToken) {
        return cloudflareService.listZones(apiToken)
                .map(resp -> ResponseEntity.ok(resp))
                .onErrorResume(e -> {
                    log.error("Failed to list zones", e);
                    return Mono.just(ResponseEntity.status(500).build());
                });
    }

    @GetMapping("/zones/{zoneId}")
    public Mono<ResponseEntity<JsonNode>> getZone(@RequestHeader("X-Cf-Token") String apiToken,
                                                   @PathVariable String zoneId) {
        return cloudflareService.getZone(apiToken, zoneId)
                .map(resp -> ResponseEntity.ok(resp));
    }

    // ===== DNS Records =====

    @GetMapping("/zones/{zoneId}/dns")
    public Mono<ResponseEntity<JsonNode>> listDns(@RequestHeader("X-Cf-Token") String apiToken,
                                                   @PathVariable String zoneId) {
        return cloudflareService.listDnsRecords(apiToken, zoneId)
                .map(resp -> ResponseEntity.ok(resp));
    }

    @PostMapping("/zones/{zoneId}/dns")
    public Mono<ResponseEntity<JsonNode>> createDns(@RequestHeader("X-Cf-Token") String apiToken,
                                                    @PathVariable String zoneId,
                                                    @RequestBody String body) {
        return cloudflareService.createDnsRecord(apiToken, zoneId, body)
                .map(resp -> ResponseEntity.status(201).body(resp));
    }

    @PutMapping("/zones/{zoneId}/dns/{recordId}")
    public Mono<ResponseEntity<JsonNode>> updateDns(@RequestHeader("X-Cf-Token") String apiToken,
                                                    @PathVariable String zoneId,
                                                    @PathVariable String recordId,
                                                    @RequestBody String body) {
        return cloudflareService.updateDnsRecord(apiToken, zoneId, recordId, body)
                .map(resp -> ResponseEntity.ok(resp));
    }

    @DeleteMapping("/zones/{zoneId}/dns/{recordId}")
    public Mono<ResponseEntity<JsonNode>> deleteDns(@RequestHeader("X-Cf-Token") String apiToken,
                                                    @PathVariable String zoneId,
                                                    @PathVariable String recordId) {
        return cloudflareService.deleteDnsRecord(apiToken, zoneId, recordId)
                .map(resp -> ResponseEntity.ok(resp));
    }

    // ===== Firewall Rules =====

    @GetMapping("/zones/{zoneId}/firewall/rules")
    public Mono<ResponseEntity<JsonNode>> listFirewall(@RequestHeader("X-Cf-Token") String apiToken,
                                                        @PathVariable String zoneId) {
        return cloudflareService.listFirewallRules(apiToken, zoneId)
                .map(resp -> ResponseEntity.ok(resp));
    }

    @PostMapping("/zones/{zoneId}/firewall/rules")
    public Mono<ResponseEntity<JsonNode>> createFirewall(@RequestHeader("X-Cf-Token") String apiToken,
                                                          @PathVariable String zoneId,
                                                          @RequestBody String body) {
        return cloudflareService.createFirewallRule(apiToken, zoneId, body)
                .map(resp -> ResponseEntity.status(201).body(resp));
    }

    @PutMapping("/zones/{zoneId}/firewall/rules/{ruleId}")
    public Mono<ResponseEntity<JsonNode>> updateFirewall(@RequestHeader("X-Cf-Token") String apiToken,
                                                          @PathVariable String zoneId,
                                                          @PathVariable String ruleId,
                                                          @RequestBody String body) {
        return cloudflareService.updateFirewallRule(apiToken, zoneId, ruleId, body)
                .map(resp -> ResponseEntity.ok(resp));
    }

    @DeleteMapping("/zones/{zoneId}/firewall/rules/{ruleId}")
    public Mono<ResponseEntity<JsonNode>> deleteFirewall(@RequestHeader("X-Cf-Token") String apiToken,
                                                          @PathVariable String zoneId,
                                                          @PathVariable String ruleId) {
        return cloudflareService.deleteFirewallRule(apiToken, zoneId, ruleId)
                .map(resp -> ResponseEntity.ok(resp));
    }

    // ===== SSL =====

    @GetMapping("/zones/{zoneId}/ssl")
    public Mono<ResponseEntity<JsonNode>> getSsl(@RequestHeader("X-Cf-Token") String apiToken,
                                                  @PathVariable String zoneId) {
        return cloudflareService.getSslSettings(apiToken, zoneId)
                .map(resp -> ResponseEntity.ok(resp));
    }

    @PatchMapping("/zones/{zoneId}/ssl")
    public Mono<ResponseEntity<JsonNode>> updateSsl(@RequestHeader("X-Cf-Token") String apiToken,
                                                    @PathVariable String zoneId,
                                                    @RequestBody Map<String, String> body) {
        return cloudflareService.updateSslMode(apiToken, zoneId, body.get("value"))
                .map(resp -> ResponseEntity.ok(resp));
    }

    // ===== Cache =====

    @PostMapping("/zones/{zoneId}/cache/purge")
    public Mono<ResponseEntity<JsonNode>> purgeAll(@RequestHeader("X-Cf-Token") String apiToken,
                                                    @PathVariable String zoneId) {
        return cloudflareService.purgeAll(apiToken, zoneId)
                .map(resp -> ResponseEntity.ok(resp));
    }

    @PostMapping("/zones/{zoneId}/cache/purge/urls")
    public Mono<ResponseEntity<JsonNode>> purgeUrls(@RequestHeader("X-Cf-Token") String apiToken,
                                                     @PathVariable String zoneId,
                                                     @RequestBody Map<String, List<String>> body) {
        return cloudflareService.purgeByUrls(apiToken, zoneId, body.get("files"))
                .map(resp -> ResponseEntity.ok(resp));
    }

    @PostMapping("/zones/{zoneId}/cache/purge/tags")
    public Mono<ResponseEntity<JsonNode>> purgeTags(@RequestHeader("X-Cf-Token") String apiToken,
                                                     @PathVariable String zoneId,
                                                     @RequestBody Map<String, List<String>> body) {
        return cloudflareService.purgeByTags(apiToken, zoneId, body.get("tags"))
                .map(resp -> ResponseEntity.ok(resp));
    }

    @PostMapping("/zones/{zoneId}/cache/purge/hosts")
    public Mono<ResponseEntity<JsonNode>> purgeHosts(@RequestHeader("X-Cf-Token") String apiToken,
                                                     @PathVariable String zoneId,
                                                     @RequestBody Map<String, List<String>> body) {
        return cloudflareService.purgeByHosts(apiToken, zoneId, body.get("hosts"))
                .map(resp -> ResponseEntity.ok(resp));
    }
}
