package com.backend.domain.controller;

import com.backend.utils.dto.ApiResponseDto;
import com.backend.domain.service.DomainService;
import com.backend.utils.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/domain")
@RequiredArgsConstructor
public class DomainController {

    private final DomainService domainService;

    @GetMapping("/zones")
    public ResponseEntity<ApiResponseDto<List<Map<String, Object>>>> listZones() {
        return ResponseEntity.ok(ApiResponseDto.success("ok", domainService.listZones()));
    }

    @GetMapping("/groups")
    public ResponseEntity<ApiResponseDto<List<Map<String, Object>>>> listGroups() {
        return ResponseEntity.ok(ApiResponseDto.success("ok", domainService.listGroups()));
    }

    @PostMapping("/groups")
    public ResponseEntity<ApiResponseDto<Map<String, Object>>> createGroup(@RequestBody Map<String, String> body) {
        String name = body.getOrDefault("name", "").strip();
        if (name.isBlank()) throw new BizException(400, "name is required");
        return ResponseEntity.ok(ApiResponseDto.success("Group created", domainService.createGroup(name)));
    }

    @PutMapping("/groups/{groupId}")
    public ResponseEntity<ApiResponseDto<Map<String, Object>>> updateGroup(
            @PathVariable String groupId, @RequestBody Map<String, String> body) {
        String name = body.getOrDefault("name", "").strip();
        if (name.isBlank()) throw new BizException(400, "name is required");
        return ResponseEntity.ok(ApiResponseDto.success("Group updated", domainService.updateGroup(groupId, name)));
    }

    @DeleteMapping("/groups/{groupId}")
    public ResponseEntity<ApiResponseDto<Void>> deleteGroup(@PathVariable String groupId) {
        domainService.deleteGroup(groupId);
        return ResponseEntity.ok(ApiResponseDto.success("Group deleted", null));
    }

    @GetMapping("/meta")
    public ResponseEntity<ApiResponseDto<List<Map<String, Object>>>> listMeta() {
        return ResponseEntity.ok(ApiResponseDto.success("ok", domainService.listMeta()));
    }

    @GetMapping("/meta/{zoneId}")
    public ResponseEntity<ApiResponseDto<Map<String, Object>>> getMeta(@PathVariable String zoneId) {
        return ResponseEntity.ok(ApiResponseDto.success("ok", domainService.getMeta(zoneId)));
    }

    @PostMapping("/meta")
    public ResponseEntity<ApiResponseDto<Map<String, Object>>> upsertMeta(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponseDto.success("Meta saved", domainService.upsertMeta(body)));
    }

    @PostMapping("/meta/batch")
    public ResponseEntity<ApiResponseDto<Map<String, Object>>> batchUpsertMeta(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        if (items == null || items.isEmpty()) throw new BizException(400, "items is required");
        int count = domainService.batchUpsertMeta(items);
        return ResponseEntity.ok(ApiResponseDto.success("Batch updated " + count + " items", Map.of("updated", count)));
    }

    @DeleteMapping("/meta/{zoneId}")
    public ResponseEntity<ApiResponseDto<Void>> deleteMeta(@PathVariable String zoneId) {
        domainService.deleteMeta(zoneId);
        return ResponseEntity.ok(ApiResponseDto.success("Meta deleted", null));
    }
}