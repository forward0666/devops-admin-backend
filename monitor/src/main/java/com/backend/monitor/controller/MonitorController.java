package com.backend.monitor.controller;

import com.backend.utils.dto.ApiResponseDto;
import com.backend.monitor.service.MonitorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Monitor rules controller.
 * All exceptions are handled by GlobalExceptionHandler (utils-core).
 */
@Slf4j
@RestController
@RequestMapping("/rules")
@RequiredArgsConstructor
public class MonitorController {

    private final MonitorService monitorService;

    @GetMapping
    public ResponseEntity<ApiResponseDto<?>> listRules(@RequestParam(required = false) Boolean enabled) {
        return ResponseEntity.ok(ApiResponseDto.success("ok", monitorService.listRules(enabled)));
    }

    @GetMapping("/{ruleId}")
    public ResponseEntity<ApiResponseDto<?>> getRule(@PathVariable Long ruleId) {
        var rule = monitorService.getRule(ruleId);
        if (rule == null) {
            return ResponseEntity.ok(ApiResponseDto.error(404, "Rule not found"));
        }
        return ResponseEntity.ok(ApiResponseDto.success("ok", rule));
    }

    @PostMapping
    public ResponseEntity<ApiResponseDto<?>> createRule(@RequestBody Map<String, Object> body) {
        monitorService.createRule(body);
        return ResponseEntity.ok(ApiResponseDto.success("Rule created", null));
    }

    @PutMapping("/{ruleId}")
    public ResponseEntity<ApiResponseDto<?>> updateRule(@PathVariable Long ruleId, @RequestBody Map<String, Object> body) {
        monitorService.updateRule(ruleId, body);
        return ResponseEntity.ok(ApiResponseDto.success("Rule updated", null));
    }

    @DeleteMapping("/{ruleId}")
    public ResponseEntity<ApiResponseDto<?>> deleteRule(@PathVariable Long ruleId) {
        monitorService.deleteRule(ruleId);
        return ResponseEntity.ok(ApiResponseDto.success("Rule deleted", null));
    }

    @PostMapping("/{ruleId}/check")
    public ResponseEntity<ApiResponseDto<?>> checkRule(@PathVariable Long ruleId) {
        return ResponseEntity.ok(ApiResponseDto.success("ok", monitorService.checkRule(ruleId)));
    }

    @PostMapping("/checkAll")
    public ResponseEntity<ApiResponseDto<?>> checkAll() {
        return ResponseEntity.ok(ApiResponseDto.success("ok", monitorService.checkAll()));
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponseDto<?>> getStatus() {
        return ResponseEntity.ok(ApiResponseDto.success("ok", monitorService.getStatusSummary()));
    }
}