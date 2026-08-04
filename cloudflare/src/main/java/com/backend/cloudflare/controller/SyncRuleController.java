package com.backend.cloudflare.controller;

import com.backend.utils.dto.ApiResponseDto;
import com.backend.cloudflare.service.SyncRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/syncRules")
@RequiredArgsConstructor
public class SyncRuleController {

    private final SyncRuleService syncRuleService;

    @GetMapping
    public ResponseEntity<ApiResponseDto<?>> list(
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) Boolean enabled) {
        return ResponseEntity.ok(ApiResponseDto.success("success", syncRuleService.list(accountId, enabled)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseDto<?>> get(@PathVariable Long id) {
        var rule = syncRuleService.getById(id);
        if (rule == null) {
            return ResponseEntity.ok(ApiResponseDto.error(404, "Rule not found"));
        }
        return ResponseEntity.ok(ApiResponseDto.success("success", rule));
    }

    @PostMapping
    public ResponseEntity<ApiResponseDto<?>> create(@RequestBody Map<String, Object> body) {
        syncRuleService.create(body);
        return ResponseEntity.ok(ApiResponseDto.success("Rule created", null));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponseDto<?>> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        syncRuleService.update(id, body);
        return ResponseEntity.ok(ApiResponseDto.success("Rule updated", null));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponseDto<?>> delete(@PathVariable Long id) {
        syncRuleService.delete(id);
        return ResponseEntity.ok(ApiResponseDto.success("Rule deleted", null));
    }

    @PostMapping("/{id}/push")
    public ResponseEntity<ApiResponseDto<?>> push(@PathVariable Long id) {
        syncRuleService.push(id);
        return ResponseEntity.ok(ApiResponseDto.success("Push completed", null));
    }
}