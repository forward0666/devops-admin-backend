package com.backend.cloudflare.controller;

import com.backend.utils.dto.ApiResponseDto;
import com.backend.cloudflare.entity.CfSyncRuleEntity;
import com.backend.cloudflare.service.SyncRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/syncRules")
@RequiredArgsConstructor
public class SyncRuleController {

    private final SyncRuleService syncRuleService;

    @GetMapping
    public ResponseEntity<ApiResponseDto<List<CfSyncRuleEntity>>> list(
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) Boolean enabled) {
        List<CfSyncRuleEntity> rules = accountId != null && accountId > 0
                ? syncRuleService.listByAccount(accountId)
                : syncRuleService.listAll();
        return ResponseEntity.ok(ApiResponseDto.success("success", rules));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseDto<CfSyncRuleEntity>> get(@PathVariable Long id) {
        CfSyncRuleEntity rule = syncRuleService.getById(id);
        if (rule == null) {
            return ResponseEntity.status(404).body(ApiResponseDto.error(404, "Rule not found"));
        }
        return ResponseEntity.ok(ApiResponseDto.success("success", rule));
    }

    @PostMapping
    public ResponseEntity<ApiResponseDto<CfSyncRuleEntity>> create(@RequestBody CfSyncRuleEntity entity) {
        CfSyncRuleEntity created = syncRuleService.create(entity);
        return ResponseEntity.ok(ApiResponseDto.success("Rule created", created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponseDto<CfSyncRuleEntity>> update(@PathVariable Long id, @RequestBody CfSyncRuleEntity entity) {
        entity.setId(id);
        CfSyncRuleEntity updated = syncRuleService.update(entity);
        return ResponseEntity.ok(ApiResponseDto.success("Rule updated", updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponseDto<Void>> delete(@PathVariable Long id) {
        syncRuleService.delete(id);
        return ResponseEntity.ok(ApiResponseDto.success("Rule deleted", null));
    }

    @PostMapping("/{id}/push")
    public ResponseEntity<ApiResponseDto<?>> push(@PathVariable Long id) {
        Map<String, Object> result = syncRuleService.executeSync(id);
        return ResponseEntity.ok(ApiResponseDto.success("Push completed", result));
    }
}
