package com.backend.cloudflare.controller;

import com.backend.utils.dto.ApiResponseDto;
import com.backend.cloudflare.service.CfZoneService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/cloudflare/cache")
@RequiredArgsConstructor
public class CacheController {

    private final CfZoneService cfZoneService;

    @PostMapping("/purge")
    public ApiResponseDto<Map<String, Object>> purgeAll(
            @RequestParam Long accountId,
            @RequestParam String zoneId,
            @RequestHeader("X-Cf-Token") String cfToken) {
        try {
            Map<String, Object> result = cfZoneService.purgeAllCache(accountId, zoneId, cfToken);
            return ApiResponseDto.success("Cache purged", result);
        } catch (Exception e) {
            log.error("Purge all cache failed: {}", e.getMessage(), e);
            return ApiResponseDto.error("Purge failed: " + e.getMessage());
        }
    }

    @PostMapping("/purgeByUrls")
    public ApiResponseDto<Map<String, Object>> purgeByUrls(
            @RequestParam Long accountId,
            @RequestParam String zoneId,
            @RequestHeader("X-Cf-Token") String cfToken,
            @RequestBody Map<String, Object> body) {
        try {
            List<String> files = (List<String>) body.getOrDefault("files", List.of());
            Map<String, Object> result = cfZoneService.purgeCacheByUrls(accountId, zoneId, cfToken, files);
            return ApiResponseDto.success("Cache purged by URLs", result);
        } catch (Exception e) {
            log.error("Purge by URLs failed: {}", e.getMessage(), e);
            return ApiResponseDto.error("Purge by URLs failed: " + e.getMessage());
        }
    }

    @PostMapping("/purgeByPrefixes")
    public ApiResponseDto<Map<String, Object>> purgeByPrefixes(
            @RequestParam Long accountId,
            @RequestParam String zoneId,
            @RequestHeader("X-Cf-Token") String cfToken,
            @RequestBody Map<String, Object> body) {
        try {
            List<String> prefixes = (List<String>) body.getOrDefault("prefixes", List.of());
            Map<String, Object> result = cfZoneService.purgeCacheByPrefixes(accountId, zoneId, cfToken, prefixes);
            return ApiResponseDto.success("Cache purged by prefixes", result);
        } catch (Exception e) {
            log.error("Purge by prefixes failed: {}", e.getMessage(), e);
            return ApiResponseDto.error("Purge by prefixes failed: " + e.getMessage());
        }
    }

    @PostMapping("/purgeByHosts")
    public ApiResponseDto<Map<String, Object>> purgeByHosts(
            @RequestParam Long accountId,
            @RequestParam String zoneId,
            @RequestHeader("X-Cf-Token") String cfToken,
            @RequestBody Map<String, Object> body) {
        try {
            List<String> hosts = (List<String>) body.getOrDefault("hosts", List.of());
            Map<String, Object> result = cfZoneService.purgeCacheByHosts(accountId, zoneId, cfToken, hosts);
            return ApiResponseDto.success("Cache purged by hosts", result);
        } catch (Exception e) {
            log.error("Purge by hosts failed: {}", e.getMessage(), e);
            return ApiResponseDto.error("Purge by hosts failed: " + e.getMessage());
        }
    }

    @PostMapping("/purgeByTags")
    public ApiResponseDto<Map<String, Object>> purgeByTags(
            @RequestParam Long accountId,
            @RequestParam String zoneId,
            @RequestHeader("X-Cf-Token") String cfToken,
            @RequestBody Map<String, Object> body) {
        try {
            List<String> tags = (List<String>) body.getOrDefault("tags", List.of());
            Map<String, Object> result = cfZoneService.purgeCacheByTags(accountId, zoneId, cfToken, tags);
            return ApiResponseDto.success("Cache purged by tags", result);
        } catch (Exception e) {
            log.error("Purge by tags failed: {}", e.getMessage(), e);
            return ApiResponseDto.error("Purge by tags failed: " + e.getMessage());
        }
    }

    @GetMapping("/rule")
    public ApiResponseDto<List<Map<String, Object>>> listCacheRules(
            @RequestParam Long accountId,
            @RequestParam String zoneId) {
        try {
            List<Map<String, Object>> rules = cfZoneService.listCacheRules(accountId, zoneId);
            return ApiResponseDto.success("ok", rules);
        } catch (Exception e) {
            log.error("List cache rules failed: {}", e.getMessage(), e);
            return ApiResponseDto.error("List cache rules failed: " + e.getMessage());
        }
    }

    @PostMapping("/rule")
    public ApiResponseDto<Map<String, Object>> createCacheRule(
            @RequestParam Long accountId,
            @RequestParam String zoneId,
            @RequestHeader("X-Cf-Token") String cfToken,
            @RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> result = cfZoneService.createCacheRule(accountId, zoneId, cfToken, body);
            return ApiResponseDto.success("Cache rule created", result);
        } catch (Exception e) {
            log.error("Create cache rule failed: {}", e.getMessage(), e);
            return ApiResponseDto.error("Create failed: " + e.getMessage());
        }
    }

    @PutMapping("/rule")
    public ApiResponseDto<Map<String, Object>> updateCacheRule(
            @RequestParam Long accountId,
            @RequestParam String zoneId,
            @RequestParam String ruleId,
            @RequestHeader("X-Cf-Token") String cfToken,
            @RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> result = cfZoneService.updateCacheRule(accountId, zoneId, ruleId, cfToken, body);
            return ApiResponseDto.success("Cache rule updated", result);
        } catch (Exception e) {
            log.error("Update cache rule failed: {}", e.getMessage(), e);
            return ApiResponseDto.error("Update failed: " + e.getMessage());
        }
    }

    @DeleteMapping("/rule")
    public ApiResponseDto<Map<String, Object>> deleteCacheRule(
            @RequestParam Long accountId,
            @RequestParam String zoneId,
            @RequestParam String ruleId,
            @RequestHeader("X-Cf-Token") String cfToken) {
        try {
            Map<String, Object> result = cfZoneService.deleteCacheRule(accountId, zoneId, ruleId, cfToken);
            return ApiResponseDto.success("Cache rule deleted", result);
        } catch (Exception e) {
            log.error("Delete cache rule failed: {}", e.getMessage(), e);
            return ApiResponseDto.error("Delete failed: " + e.getMessage());
        }
    }
}