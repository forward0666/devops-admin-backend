package com.backend.cloudflare.service;

import com.backend.cloudflare.config.CfApiConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CfClientService {

    private final RestTemplate restTemplate;
    private final CfApiConfig cfApiConfig;

    // ---- Headers / HTTP helpers ----

    private HttpHeaders authHeaders(String apiToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private <T> ResponseEntity<T> get(String url, String apiToken, Class<T> responseType, Object... uriVars) {
        HttpEntity<?> entity = new HttpEntity<>(authHeaders(apiToken));
        return restTemplate.exchange(url, HttpMethod.GET, entity, responseType, uriVars);
    }

    private <T> ResponseEntity<T> post(String url, String apiToken, Object body, Class<T> responseType, Object... uriVars) {
        HttpEntity<Object> entity = new HttpEntity<>(body, authHeaders(apiToken));
        return restTemplate.exchange(url, HttpMethod.POST, entity, responseType, uriVars);
    }

    private <T> ResponseEntity<T> patch(String url, String apiToken, Object body, Class<T> responseType, Object... uriVars) {
        HttpEntity<Object> entity = new HttpEntity<>(body, authHeaders(apiToken));
        return restTemplate.exchange(url, HttpMethod.PATCH, entity, responseType, uriVars);
    }

    private <T> ResponseEntity<T> put(String url, String apiToken, Object body, Class<T> responseType, Object... uriVars) {
        HttpEntity<Object> entity = new HttpEntity<>(body, authHeaders(apiToken));
        return restTemplate.exchange(url, HttpMethod.PUT, entity, responseType, uriVars);
    }

    private <T> ResponseEntity<T> delete(String url, String apiToken, Class<T> responseType, Object... uriVars) {
        HttpEntity<?> entity = new HttpEntity<>(authHeaders(apiToken));
        return restTemplate.exchange(url, HttpMethod.DELETE, entity, responseType, uriVars);
    }

    /**
     * Wrap a CF API call with standardized error handling.
     * Logs the error, returns an error Map with success=false.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> safeCall(String operation, Executor<ResponseEntity<Map>> action) {
        try {
            ResponseEntity<Map> resp = action.execute();
            return resp.getBody();
        } catch (Exception e) {
            log.error("{} failed: {}", operation, e.getMessage());
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("errors", List.of(Map.of("message", e.getMessage())));
            return error;
        }
    }

    /**
     * Wrap a CF API call with pagination (listZones / listDnsRecords pattern).
     * Automatically fetches all pages and merges them.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> safePaginated(String operation, java.util.function.Function<Integer, ResponseEntity<Map>> pageFetcher) {
        List<Map<String, Object>> allResults = new ArrayList<>();
        Map<String, Object> resultInfo = null;
        boolean success = true;
        List<Object> errors = new ArrayList<>();

        try {
            int page = 1;
            boolean hasMore = true;

            while (hasMore) {
                ResponseEntity<Map> resp = pageFetcher.apply(page);
                Map<String, Object> body = resp.getBody();
                if (body == null) break;

                success = (Boolean) body.getOrDefault("success", false);
                if (!success) {
                    errors = (List<Object>) body.getOrDefault("errors", List.of());
                    break;
                }

                List<Map<String, Object>> pageResults = (List<Map<String, Object>>) body.getOrDefault("result", List.of());
                allResults.addAll(pageResults);

                resultInfo = (Map<String, Object>) body.getOrDefault("result_info", Map.of());
                int totalPages = resultInfo != null ? ((Number) resultInfo.getOrDefault("total_pages", 0)).intValue() : 0;
                hasMore = page < totalPages;
                page++;
            }
        } catch (Exception e) {
            log.error("{} failed: {}", operation, e.getMessage());
            success = false;
            errors = List.of(Map.of("message", e.getMessage()));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        result.put("result", allResults);
        result.put("result_info", resultInfo != null ? resultInfo : Map.of());
        if (!errors.isEmpty()) {
            result.put("errors", errors);
        }
        return result;
    }

    @FunctionalInterface
    private interface Executor<T> {
        T execute();
    }

    // ---- Token & Account ----

    /**
     * GET /user/tokens/verify — verify an API token is valid.
     */
    public Map<String, Object> verifyToken(String apiToken) {
        String url = cfApiConfig.getBaseUrl() + "/user/tokens/verify";
        return safeCall("verifyToken", () -> get(url, apiToken, Map.class));
    }

    /**
     * GET /accounts — list all accounts accessible by the token.
     */
    public Map<String, Object> listAccounts(String apiToken) {
        String url = cfApiConfig.getBaseUrl() + "/accounts?per_page=50";
        return safeCall("listAccounts", () -> get(url, apiToken, Map.class));
    }

    // ---- Zones ----

    /**
     * GET /zones — list zones with pagination support.
     */
    public Map<String, Object> listZones(String apiToken, int perPage, String accountId) {
        StringBuilder urlBuilder = new StringBuilder(cfApiConfig.getBaseUrl())
                .append("/zones?per_page=").append(perPage);
        if (accountId != null && !accountId.isBlank()) {
            urlBuilder.append("&account.id=").append(accountId);
        }
        String baseUrl = urlBuilder.toString();
        return safePaginated("listZones", page -> get(baseUrl + "&page=" + page, apiToken, Map.class));
    }

    /**
     * GET /zones/{zone_id} — get a single zone.
     */
    public Map<String, Object> getZone(String apiToken, String zoneId) {
        String url = cfApiConfig.getBaseUrl() + "/zones/{zoneId}";
        return safeCall("getZone", () -> get(url, apiToken, Map.class, zoneId));
    }

    // ---- DNS Records ----

    /**
     * GET /zones/{zone_id}/dns_records — list DNS records with pagination.
     */
    public Map<String, Object> listDnsRecords(String apiToken, String zoneId, int perPage) {
        String baseUrl = cfApiConfig.getBaseUrl() + "/zones/{zoneId}/dns_records?per_page={perPage}&page=";
        return safePaginated("listDnsRecords", page -> get(baseUrl + page, apiToken, Map.class, zoneId, perPage));
    }

    /**
     * POST /zones/{zone_id}/dns_records — create a DNS record.
     */
    public Map<String, Object> createDnsRecord(String apiToken, String zoneId, Map<String, Object> data) {
        String url = cfApiConfig.getBaseUrl() + "/zones/{zoneId}/dns_records";
        return safeCall("createDnsRecord", () -> post(url, apiToken, data, Map.class, zoneId));
    }

    /**
     * PATCH /zones/{zone_id}/dns_records/{record_id} — update a DNS record.
     */
    public Map<String, Object> updateDnsRecord(String apiToken, String zoneId, String recordId, Map<String, Object> data) {
        String url = cfApiConfig.getBaseUrl() + "/zones/{zoneId}/dns_records/{recordId}";
        return safeCall("updateDnsRecord", () -> patch(url, apiToken, data, Map.class, zoneId, recordId));
    }

    /**
     * DELETE /zones/{zone_id}/dns_records/{record_id} — delete a DNS record.
     */
    public Map<String, Object> deleteDnsRecord(String apiToken, String zoneId, String recordId) {
        String url = cfApiConfig.getBaseUrl() + "/zones/{zoneId}/dns_records/{recordId}";
        return safeCall("deleteDnsRecord", () -> delete(url, apiToken, Map.class, zoneId, recordId));
    }

    // ---- Cache ----

    /**
     * POST /zones/{zone_id}/purge_cache — purge cache with a custom body.
     */
    public Map<String, Object> purgeCache(String apiToken, String zoneId, Map<String, Object> body) {
        String url = cfApiConfig.getBaseUrl() + "/zones/{zoneId}/purge_cache";
        return safeCall("purgeCache", () -> post(url, apiToken, body, Map.class, zoneId));
    }

    /**
     * POST /zones/{zone_id}/purge_cache — purge everything.
     */
    public Map<String, Object> purgeEverything(String apiToken, String zoneId) {
        Map<String, Object> body = Map.of("purge_everything", true);
        return purgeCache(apiToken, zoneId, body);
    }

    // ---- Zone Settings ----

    /**
     * GET /zones/{zone_id}/settings — get all zone settings.
     */
    public Map<String, Object> getZoneSettings(String apiToken, String zoneId) {
        String url = cfApiConfig.getBaseUrl() + "/zones/{zoneId}/settings";
        return safeCall("getZoneSettings", () -> get(url, apiToken, Map.class, zoneId));
    }

    /**
     * GET /zones/{zone_id}/settings/ssl — get SSL setting.
     */
    public Map<String, Object> getSslSetting(String apiToken, String zoneId) {
        String url = cfApiConfig.getBaseUrl() + "/zones/{zoneId}/settings/ssl";
        return safeCall("getSslSetting", () -> get(url, apiToken, Map.class, zoneId));
    }

    /**
     * PATCH /zones/{zone_id}/settings/ssl — update SSL setting.
     */
    public Map<String, Object> updateSslSetting(String apiToken, String zoneId, Map<String, Object> data) {
        String url = cfApiConfig.getBaseUrl() + "/zones/{zoneId}/settings/ssl";
        return safeCall("updateSslSetting", () -> patch(url, apiToken, data, Map.class, zoneId));
    }

    /**
     * PATCH /zones/{zone_id}/settings — update multiple zone settings at once.
     */
    public Map<String, Object> updateZoneSettings(String apiToken, String zoneId, Map<String, Object> settings) {
        String url = cfApiConfig.getBaseUrl() + "/zones/{zoneId}/settings";
        return safeCall("updateZoneSettings", () -> patch(url, apiToken, settings, Map.class, zoneId));
    }

    // ---- Rulesets ----

    /**
     * GET /zones/{zone_id}/rulesets — list all rulesets for a zone.
     */
    public Map<String, Object> listRulesets(String apiToken, String zoneId) {
        String url = cfApiConfig.getBaseUrl() + "/zones/{zoneId}/rulesets";
        return safeCall("listRulesets", () -> get(url, apiToken, Map.class, zoneId));
    }

    /**
     * GET /zones/{zone_id}/rulesets/{ruleset_id} — get a specific ruleset.
     */
    public Map<String, Object> getRuleset(String apiToken, String zoneId, String rulesetId) {
        String url = cfApiConfig.getBaseUrl() + "/zones/{zoneId}/rulesets/{rulesetId}";
        return safeCall("getRuleset", () -> get(url, apiToken, Map.class, zoneId, rulesetId));
    }

    /**
     * POST /zones/{zone_id}/rulesets — create a new ruleset.
     */
    public Map<String, Object> createRuleset(String apiToken, String zoneId, Map<String, Object> body) {
        String url = cfApiConfig.getBaseUrl() + "/zones/{zoneId}/rulesets";
        return safeCall("createRuleset", () -> post(url, apiToken, body, Map.class, zoneId));
    }

    /**
     * PUT /zones/{zone_id}/rulesets/phases/{phase} — set rules for a phase.
     */
    public Map<String, Object> updateRulesetPhases(String apiToken, String zoneId, String phase, List<Map<String, Object>> rules) {
        String url = cfApiConfig.getBaseUrl() + "/zones/{zoneId}/rulesets/phases/{phase}";
        Map<String, Object> body = Map.of("rules", rules);
        return safeCall("updateRulesetPhases", () -> put(url, apiToken, body, Map.class, zoneId, phase));
    }

    /**
     * GET /zones/{zone_id}/rulesets/phases/{phase} — get rules for a phase.
     */
    public Map<String, Object> getRulesetPhase(String apiToken, String zoneId, String phase) {
        String url = cfApiConfig.getBaseUrl() + "/zones/{zoneId}/rulesets/phases/{phase}";
        return safeCall("getRulesetPhase", () -> get(url, apiToken, Map.class, zoneId, phase));
    }

    /**
     * POST /zones/{zone_id}/rulesets/{ruleset_id}/rules — add a rule to a ruleset.
     */
    public Map<String, Object> createRulesetRule(String apiToken, String zoneId, String rulesetId, Map<String, Object> rule) {
        String url = cfApiConfig.getBaseUrl() + "/zones/{zoneId}/rulesets/{rulesetId}/rules";
        return safeCall("createRulesetRule", () -> post(url, apiToken, rule, Map.class, zoneId, rulesetId));
    }

    /**
     * PATCH /zones/{zone_id}/rulesets/{ruleset_id}/rules/{rule_id} — update a rule.
     */
    public Map<String, Object> updateRulesetRule(String apiToken, String zoneId, String rulesetId, String ruleId, Map<String, Object> rule) {
        String url = cfApiConfig.getBaseUrl() + "/zones/{zoneId}/rulesets/{rulesetId}/rules/{ruleId}";
        return safeCall("updateRulesetRule", () -> patch(url, apiToken, rule, Map.class, zoneId, rulesetId, ruleId));
    }

    // ---- GraphQL Analytics ----

    /**
     * POST /graphql — execute a GraphQL analytics query.
     */
    public Map<String, Object> analyticsQuery(String apiToken, Map<String, Object> queryBody) {
        String url = cfApiConfig.getBaseUrl() + "/graphql";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(queryBody, headers);
        return safeCall("analyticsQuery", () -> restTemplate.postForEntity(url, entity, Map.class));
    }

    /**
     * DELETE /zones/{zone_id}/rulesets/{ruleset_id}/rules/{rule_id} — delete a rule.
     */
    public Map<String, Object> deleteRulesetRule(String apiToken, String zoneId, String rulesetId, String ruleId) {
        String url = cfApiConfig.getBaseUrl() + "/zones/{zoneId}/rulesets/{rulesetId}/rules/{ruleId}";
        return safeCall("deleteRulesetRule", () -> delete(url, apiToken, Map.class, zoneId, rulesetId, ruleId));
    }
}