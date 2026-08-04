package com.backend.domain.controller;

import com.backend.utils.dto.ApiResponseDto;
import com.backend.domain.service.SyncDomainService;
import com.backend.utils.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/syncDomain")
@RequiredArgsConstructor
public class SyncDomainController {

    private final SyncDomainService syncDomainService;

    @GetMapping("/rules")
    public ResponseEntity<ApiResponseDto<?>> listRules() {
        return ResponseEntity.ok(ApiResponseDto.success("ok", syncDomainService.listRules()));
    }

    @GetMapping("/rules/{ruleId}")
    public ResponseEntity<ApiResponseDto<?>> getRule(@PathVariable Long ruleId) {
        var rule = syncDomainService.getRule(ruleId);
        if (rule == null) throw new BizException(404, "Rule not found");
        return ResponseEntity.ok(ApiResponseDto.success("ok", rule));
    }

    @PostMapping("/rules")
    public ResponseEntity<ApiResponseDto<?>> createRule(@RequestBody Map<String, Object> body) {
        syncDomainService.createRule(body);
        return ResponseEntity.ok(ApiResponseDto.success("Rule created", null));
    }

    @PutMapping("/rules/{ruleId}")
    public ResponseEntity<ApiResponseDto<?>> updateRule(@PathVariable Long ruleId, @RequestBody Map<String, Object> body) {
        syncDomainService.updateRule(ruleId, body);
        return ResponseEntity.ok(ApiResponseDto.success("Rule updated", null));
    }

    @DeleteMapping("/rules/{ruleId}")
    public ResponseEntity<ApiResponseDto<?>> deleteRule(@PathVariable Long ruleId) {
        syncDomainService.deleteRule(ruleId);
        return ResponseEntity.ok(ApiResponseDto.success("Rule deleted", null));
    }

    @PostMapping("/rules/{ruleId}/check")
    public ResponseEntity<ApiResponseDto<?>> checkRule(@PathVariable Long ruleId) {
        return ResponseEntity.ok(ApiResponseDto.success("ok", syncDomainService.checkRule(ruleId)));
    }
}