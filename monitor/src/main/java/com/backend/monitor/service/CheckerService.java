package com.backend.monitor.service;

import com.backend.monitor.entity.MonitorEntity;
import com.backend.monitor.mapper.MonitorMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * Advanced check service that performs HTTP/DNS probing for domain monitoring.
 * Mirrors the Python checker.py logic — checks domains from MongoDB, resolves DNS,
 * sends HTTP probes, and stores results back to MongoDB.
 *
 * This is a background task runner invoked by MonitorService.checkRule().
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckerService {

    private final MonitorMapper monitorMapper;
    private final MongoTemplate mongoTemplate;

    // Concurrency limits (same as Python default: HTTP=50, DNS=50)
    private static final int HTTP_CONCURRENCY = 50;
    private static final int DNS_CONCURRENCY = 50;
    private static final int TIMEOUT_SECONDS = 3;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private final Semaphore httpSemaphore = new Semaphore(HTTP_CONCURRENCY);
    private final Semaphore dnsSemaphore = new Semaphore(DNS_CONCURRENCY);

    /**
     * Run a full check for a given rule:
     * 1. Load all (non-ignored) domains from MongoDB domain collection
     * 2. Pre-resolve DNS for all domains
     * 3. Concurrent HTTP probe with semaphore
     * 4. Write results to MongoDB monitor_results_{ruleId}
     * 5. Update rule status
     */
    public void runCheckForRule(Long ruleId) {
        log.info("[Checker] Starting check for rule {}", ruleId);
        var rule = monitorMapper.selectById(ruleId);
        if (rule == null) {
            log.warn("[Checker] Rule {} not found", ruleId);
            return;
        }

        long startTime = System.currentTimeMillis();

        try {
            // 1. Load domains from MongoDB
            List<String> domainsToCheck = loadDomains();
            if (domainsToCheck.isEmpty()) {
                log.warn("[Checker] No domains to check for rule {}", ruleId);
                return;
            }
            log.info("[Checker] Loaded {} domains", domainsToCheck.size());

            // 2. Update rule status
            rule.setStatus("running");
            monitorMapper.updateById(rule);

            // 3. Pre-resolve DNS
            dnsPreResolve(domainsToCheck);

            // 4. HTTP checks
            List<Map<String, Object>> results = runHttpChecks(domainsToCheck);

            // 5. Save results to MongoDB
            saveResults(ruleId, rule.getName(), results);

            // 6. Update rule status
            long upCount = results.stream().filter(r -> "up".equals(r.get("status"))).count();
            long downCount = results.stream().filter(r -> "timeout".equals(r.get("status"))).count();
            String overallStatus = (downCount == 0) ? "ok" : (upCount > 0 ? "warning" : "error");
            rule.setStatus(overallStatus);
            rule.setLastCheck(new Date().toString());
            monitorMapper.updateById(rule);

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("[Checker] Rule {} completed: up={}, down={}, elapsed={}ms",
                    ruleId, upCount, downCount, elapsed);

        } catch (Exception e) {
            log.error("[Checker] Rule {} failed: {}", ruleId, e.getMessage(), e);
            rule.setStatus("error");
            monitorMapper.updateById(rule);
        }
    }

    /**
     * Load all non-ignored domain names from MongoDB.
     */
    private List<String> loadDomains() {
        List<String> domains = new ArrayList<>();
        try {
            var collection = mongoTemplate.getCollection("domain");
            for (var doc : collection.find()) {
                Boolean isIgnored = doc.getBoolean("is_ignored");
                if (Boolean.TRUE.equals(isIgnored)) continue;
                String name = doc.getString("name");
                if (name != null && !name.isBlank()) {
                    domains.add(name);
                }
            }
        } catch (Exception e) {
            log.warn("[Checker] Failed to load domains from MongoDB: {}", e.getMessage());
        }
        return domains;
    }

    /**
     * DNS pre-resolve all domains in parallel.
     */
    private void dnsPreResolve(List<String> domains) {
        log.info("[Checker] DNS pre-resolving {} domains...", domains.size());
        long start = System.currentTimeMillis();

        try (var executor = new ForkJoinPool(DNS_CONCURRENCY)) {
            executor.submit(() -> domains.parallelStream().forEach(domain -> {
                try {
                    dnsSemaphore.acquire();
                    InetAddress.getByName(domain);
                } catch (Exception ignored) {
                } finally {
                    dnsSemaphore.release();
                }
            })).get();
        } catch (Exception e) {
            log.warn("[Checker] DNS pre-resolve error: {}", e.getMessage());
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("[Checker] DNS pre-resolve completed in {}ms", elapsed);
    }

    /**
     * Run concurrent HTTP probes on all domains.
     */
    private List<Map<String, Object>> runHttpChecks(List<String> domains) {
        Date now = new Date();
        List<Map<String, Object>> results = new ArrayList<>();
        var executor = Executors.newFixedThreadPool(HTTP_CONCURRENCY);
        var futures = new ArrayList<Future<Map<String, Object>>>();

        for (String domain : domains) {
            futures.add(executor.submit(() -> checkDomain(domain, now)));
        }

        for (var future : futures) {
            try {
                results.add(future.get(10, TimeUnit.SECONDS));
            } catch (Exception e) {
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("domain", "unknown");
                errorResult.put("status", "error");
                errorResult.put("error", e.getMessage());
                results.add(errorResult);
            }
        }

        executor.shutdown();
        return results;
    }

    /**
     * Check a single domain: DNS resolve + HTTP probe.
     */
    private Map<String, Object> checkDomain(String domain, Date checkedAt) {
        Map<String, Object> result = new HashMap<>();
        result.put("domain", domain);
        result.put("status", "timeout");
        result.put("status_code", null);
        result.put("response_time_ms", null);
        result.put("error", null);
        result.put("checked_at", checkedAt);

        long start = System.currentTimeMillis();

        try {
            // Try HTTP first, then HTTPS
            String[] schemes = {"http", "https"};
            for (String scheme : schemes) {
                try {
                    httpSemaphore.acquire();
                    var request = HttpRequest.newBuilder()
                            .uri(URI.create(scheme + "://" + domain))
                            .method("HEAD", HttpRequest.BodyPublishers.noBody())
                            .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                            .build();

                    HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                    long elapsed = System.currentTimeMillis() - start;

                    result.put("status_code", response.statusCode());
                    result.put("response_time_ms", elapsed);
                    result.put("status", response.statusCode() < 500 ? "up" : "down");

                    // Resolve IP
                    try {
                        result.put("resolved_ip", InetAddress.getByName(domain).getHostAddress());
                    } catch (Exception ignored) {}

                    return result;
                } catch (Exception ignored) {
                } finally {
                    httpSemaphore.release();
                }
            }

            // Both failed
            long elapsed = System.currentTimeMillis() - start;
            result.put("response_time_ms", elapsed);
            result.put("error", "Connection failed");
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            result.put("response_time_ms", elapsed);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * Save check results to MongoDB collection monitor_results_{ruleId}.
     */
    private void saveResults(Long ruleId, String ruleName, List<Map<String, Object>> results) {
        String collectionName = "monitor_results_" + ruleId;
        var collection = mongoTemplate.getCollection(collectionName);

        // Create index
        try {
            collection.createIndex(new org.bson.Document("domain", 1));
            collection.createIndex(new org.bson.Document("checked_at", -1));
        } catch (Exception e) {
            log.warn("[Checker] Index creation warning: {}", e.getMessage());
        }

        Date now = new Date();
        for (var result : results) {
            String domain = (String) result.get("domain");
            if (domain == null) continue;

            var doc = new org.bson.Document(result)
                    .append("rule_id", ruleId)
                    .append("rule_name", ruleName)
                    .append("checked_at", now);

            try {
                collection.replaceOne(
                        new org.bson.Document("rule_id", ruleId).append("domain", domain),
                        doc,
                        new com.mongodb.client.model.ReplaceOptions().upsert(true));
            } catch (Exception e) {
                log.warn("[Checker] Failed to save result for {}: {}", domain, e.getMessage());
            }
        }

        log.info("[Checker] Saved {} results to {}", results.size(), collectionName);
    }
}