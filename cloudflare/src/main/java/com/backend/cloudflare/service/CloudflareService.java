package com.backend.cloudflare.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloudflareService {

    @Value("${cf.base-url}")
    private String baseUrl;

    private final WebClient.Builder webClientBuilder;

    private WebClient getClient(String apiToken) {
        return webClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiToken)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    // ===== Account / Token Test =====

    public Mono<JsonNode> testToken(String apiToken) {
        return getClient(apiToken)
                .get().uri("/user/tokens/verify")
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    // ===== Zones =====

    public Mono<JsonNode> listZones(String apiToken) {
        return getClient(apiToken)
                .get().uri("/zones?per_page=50")
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    public Mono<JsonNode> getZone(String apiToken, String zoneId) {
        return getClient(apiToken)
                .get().uri("/zones/{zoneId}", zoneId)
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    public Mono<JsonNode> getZoneSettings(String apiToken, String zoneId) {
        return getClient(apiToken)
                .get().uri("/zones/{zoneId}/settings", zoneId)
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    // ===== DNS Records =====

    public Mono<JsonNode> listDnsRecords(String apiToken, String zoneId) {
        return getClient(apiToken)
                .get().uri("/zones/{zoneId}/dns_records?per_page=100", zoneId)
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    public Mono<JsonNode> createDnsRecord(String apiToken, String zoneId, String body) {
        return getClient(apiToken)
                .post().uri("/zones/{zoneId}/dns_records", zoneId)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    public Mono<JsonNode> updateDnsRecord(String apiToken, String zoneId, String recordId, String body) {
        return getClient(apiToken)
                .put().uri("/zones/{zoneId}/dns_records/{recordId}", zoneId, recordId)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    public Mono<JsonNode> deleteDnsRecord(String apiToken, String zoneId, String recordId) {
        return getClient(apiToken)
                .delete().uri("/zones/{zoneId}/dns_records/{recordId}", zoneId, recordId)
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    // ===== Firewall Rules =====

    public Mono<JsonNode> listFirewallRules(String apiToken, String zoneId) {
        return getClient(apiToken)
                .get().uri("/zones/{zoneId}/firewall/rules", zoneId)
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    public Mono<JsonNode> createFirewallRule(String apiToken, String zoneId, String body) {
        return getClient(apiToken)
                .post().uri("/zones/{zoneId}/firewall/rules", zoneId)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    public Mono<JsonNode> updateFirewallRule(String apiToken, String zoneId, String ruleId, String body) {
        return getClient(apiToken)
                .put().uri("/zones/{zoneId}/firewall/rules/{ruleId}", zoneId, ruleId)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    public Mono<JsonNode> deleteFirewallRule(String apiToken, String zoneId, String ruleId) {
        return getClient(apiToken)
                .delete().uri("/zones/{zoneId}/firewall/rules/{ruleId}", zoneId, ruleId)
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    // ===== SSL =====

    public Mono<JsonNode> getSslSettings(String apiToken, String zoneId) {
        return getClient(apiToken)
                .get().uri("/zones/{zoneId}/settings/ssl", zoneId)
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    public Mono<JsonNode> updateSslMode(String apiToken, String zoneId, String mode) {
        String body = String.format("{\"value\":\"%s\"}", mode);
        return getClient(apiToken)
                .patch().uri("/zones/{zoneId}/settings/ssl", zoneId)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    // ===== Cache Purge =====

    public Mono<JsonNode> purgeAll(String apiToken, String zoneId) {
        return getClient(apiToken)
                .post().uri("/zones/{zoneId}/purge_cache", zoneId)
                .bodyValue("{\"purge_everything\":true}")
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    public Mono<JsonNode> purgeByUrls(String apiToken, String zoneId, java.util.List<String> urls) {
        StringBuilder sb = new StringBuilder("{\"files\":[");
        for (int i = 0; i < urls.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(urls.get(i)).append("\"");
        }
        sb.append("]}");
        return getClient(apiToken)
                .post().uri("/zones/{zoneId}/purge_cache", zoneId)
                .bodyValue(sb.toString())
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    public Mono<JsonNode> purgeByTags(String apiToken, String zoneId, java.util.List<String> tags) {
        StringBuilder sb = new StringBuilder("{\"tags\":[");
        for (int i = 0; i < tags.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(tags.get(i)).append("\"");
        }
        sb.append("]}");
        return getClient(apiToken)
                .post().uri("/zones/{zoneId}/purge_cache", zoneId)
                .bodyValue(sb.toString())
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    public Mono<JsonNode> purgeByHosts(String apiToken, String zoneId, java.util.List<String> hosts) {
        StringBuilder sb = new StringBuilder("{\"hosts\":[");
        for (int i = 0; i < hosts.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(hosts.get(i)).append("\"");
        }
        sb.append("]}");
        return getClient(apiToken)
                .post().uri("/zones/{zoneId}/purge_cache", zoneId)
                .bodyValue(sb.toString())
                .retrieve()
                .bodyToMono(JsonNode.class);
    }
}
