package com.backend.cloudflare.controller;

import com.backend.utils.dto.ApiResponseDto;
import com.backend.cloudflare.service.CfZoneService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/cloudflare/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final CfZoneService cfZoneService;

    @PostMapping
    public ApiResponseDto<Map<String, Object>> query(
            @RequestBody Map<String, Object> body) {
        try {
            Long accountId = body.get("accountId") != null ? Long.valueOf(body.get("accountId").toString()) : null;
            String zoneId = (String) body.get("zoneId");
            String apiToken = (String) body.get("apiToken");
            @SuppressWarnings("unchecked")
            Map<String, Object> query = (Map<String, Object>) body.get("query");
            if (accountId == null || zoneId == null) {
                return ApiResponseDto.error("accountId and zoneId are required");
            }
            Map<String, Object> result = cfZoneService.queryAnalytics(accountId, zoneId, apiToken, query);
            return ApiResponseDto.success("Analytics query completed", result);
        } catch (Exception e) {
            log.error("Analytics query failed: {}", e.getMessage(), e);
            return ApiResponseDto.error("Query failed: " + e.getMessage());
        }
    }
}