package com.backend.cloudflare.controller;

import com.backend.utils.dto.ApiResponseDto;
import com.backend.cloudflare.service.CfZoneService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/cloudflare/zoneConfig")
@RequiredArgsConstructor
public class ZoneConfigController {

    private final CfZoneService cfZoneService;

    @GetMapping
    public ApiResponseDto<Map<String, Object>> getSettings(
            @RequestParam Long accountId,
            @RequestParam String zoneId) {
        try {
            Map<String, Object> result = cfZoneService.getZoneSettings(accountId, zoneId);
            return ApiResponseDto.success("ok", result);
        } catch (Exception e) {
            log.error("Get zone settings failed: {}", e.getMessage(), e);
            return ApiResponseDto.error("Get settings failed: " + e.getMessage());
        }
    }

    @PatchMapping
    public ApiResponseDto<Map<String, Object>> updateSettings(
            @RequestParam Long accountId,
            @RequestParam String zoneId,
            @RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> result = cfZoneService.updateZoneSettings(accountId, zoneId, body);
            return ApiResponseDto.success("Settings updated", result);
        } catch (Exception e) {
            log.error("Update zone settings failed: {}", e.getMessage(), e);
            return ApiResponseDto.error("Update settings failed: " + e.getMessage());
        }
    }
}