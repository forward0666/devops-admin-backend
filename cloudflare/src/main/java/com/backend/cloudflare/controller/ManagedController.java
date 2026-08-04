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
@RequestMapping("/api/cloudflare/managed")
@RequiredArgsConstructor
public class ManagedController {

    private final CfZoneService cfZoneService;

    @GetMapping
    public ApiResponseDto<List<Map<String, Object>>> listRules(
            @RequestParam Long accountId,
            @RequestParam String zoneId) {
        try {
            List<Map<String, Object>> rules = cfZoneService.listManagedRules(accountId, zoneId);
            return ApiResponseDto.success("ok", rules);
        } catch (Exception e) {
            log.error("List managed rules failed: {}", e.getMessage(), e);
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
            Map<String, Object> result = cfZoneService.createManagedRule(accountId, zoneId, cfToken, body);
            return ApiResponseDto.success("Managed rule created", result);
        } catch (Exception e) {
            log.error("Create managed rule failed: {}", e.getMessage(), e);
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
            Map<String, Object> result = cfZoneService.updateManagedRule(accountId, zoneId, ruleId, cfToken, body);
            return ApiResponseDto.success("Managed rule updated", result);
        } catch (Exception e) {
            log.error("Update managed rule failed: {}", e.getMessage(), e);
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
            Map<String, Object> result = cfZoneService.deleteManagedRule(accountId, zoneId, ruleId, cfToken);
            return ApiResponseDto.success("Managed rule deleted", result);
        } catch (Exception e) {
            log.error("Delete managed rule failed: {}", e.getMessage(), e);
            return ApiResponseDto.error("Delete failed: " + e.getMessage());
        }
    }
}