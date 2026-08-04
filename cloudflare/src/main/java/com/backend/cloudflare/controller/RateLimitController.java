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
@RequestMapping("/api/cloudflare/rateLimit")
@RequiredArgsConstructor
public class RateLimitController {

    private final CfZoneService cfZoneService;

    @GetMapping
    public ApiResponseDto<List<Map<String, Object>>> listRules(
            @RequestParam Long accountId,
            @RequestParam String zoneId) {
        try {
            List<Map<String, Object>> rules = cfZoneService.listRateLimitRules(accountId, zoneId);
            return ApiResponseDto.success("ok", rules);
        } catch (Exception e) {
            log.error("List rate limit rules failed: {}", e.getMessage(), e);
            return ApiResponseDto.error("List failed: " + e.getMessage());
        }
    }

    @PostMapping
    public ApiResponseDto<Map<String, Object>> createRule(
            @RequestParam Long accountId,
            @RequestParam String zoneId,
            @RequestHeader("X-Cf-Token") String cfToken,
            @RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> result = cfZoneService.createRateLimitRule(accountId, zoneId, cfToken, body);
            return ApiResponseDto.success("Rate limit rule created", result);
        } catch (Exception e) {
            log.error("Create rate limit rule failed: {}", e.getMessage(), e);
            return ApiResponseDto.error("Create failed: " + e.getMessage());
        }
    }

    @PutMapping("/{ruleId}")
    public ApiResponseDto<Map<String, Object>> updateRule(
            @PathVariable String ruleId,
            @RequestParam Long accountId,
            @RequestParam String zoneId,
            @RequestHeader("X-Cf-Token") String cfToken,
            @RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> result = cfZoneService.updateRateLimitRule(accountId, zoneId, ruleId, cfToken, body);
            return ApiResponseDto.success("Rate limit rule updated", result);
        } catch (Exception e) {
            log.error("Update rate limit rule failed: {}", e.getMessage(), e);
            return ApiResponseDto.error("Update failed: " + e.getMessage());
        }
    }

    @DeleteMapping("/{ruleId}")
    public ApiResponseDto<Map<String, Object>> deleteRule(
            @PathVariable String ruleId,
            @RequestParam Long accountId,
            @RequestParam String zoneId,
            @RequestHeader("X-Cf-Token") String cfToken) {
        try {
            Map<String, Object> result = cfZoneService.deleteRateLimitRule(accountId, zoneId, ruleId, cfToken);
            return ApiResponseDto.success("Rate limit rule deleted", result);
        } catch (Exception e) {
            log.error("Delete rate limit rule failed: {}", e.getMessage(), e);
            return ApiResponseDto.error("Delete failed: " + e.getMessage());
        }
    }
}