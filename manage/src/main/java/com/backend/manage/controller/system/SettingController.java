package com.backend.manage.controller.system;

import com.backend.manage.annotation.OperationLog;
import com.backend.manage.dto.ApiResponseDto;
import com.backend.manage.service.system.SettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class SettingController {

    private final SettingService systemSettingsService;

    @GetMapping({ "/setting"})
    @OperationLog(
            operationType = "SETTING_READ",
            operationName = "获取系统设置",
            resourceType = "SYSTEM_SETTING",
            description = "查看所有配置信息",
            logResponse = false
    )
    public ResponseEntity<ApiResponseDto<Map<String, Object>>> getSetting() {
        log.info("GET /setting - Fetching all setting");
        Map<String, Object> setting = systemSettingsService.getSetting();
        return ResponseEntity.ok(ApiResponseDto.success("Setting retrieved successfully", setting));
    }

    @PutMapping({ "/setting"})
    @OperationLog(
            operationType = "SETTING_UPDATE",
            operationName = "更新系统设置",
            resourceType = "SYSTEM_SETTING",
            description = "修改配置信息"
    )
    public ResponseEntity<ApiResponseDto<Map<String, Object>>> updateSetting(@RequestBody Map<String, Object> setting) {
        log.info("PUT /setting - Updating setting");
        systemSettingsService.updateSetting(setting);
        Map<String, Object> updated = systemSettingsService.getSetting();
        return ResponseEntity.ok(ApiResponseDto.success("Setting updated successfully", updated));
    }

    @GetMapping({ "/setting/captcha"})
    public ResponseEntity<ApiResponseDto<Map<String, Object>>> getCaptchaStatus() {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("enabled", systemSettingsService.isLoginCaptchaEnabled());
        return ResponseEntity.ok(ApiResponseDto.success("Captcha status retrieved", result));
    }
}
