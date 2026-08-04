package com.backend.agent.controller;

import com.backend.agent.entity.ToolEntity;
import com.backend.agent.service.ToolService;
import com.backend.utils.dto.ApiResponseDto;
import com.backend.utils.exception.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Tool CRUD endpoints.
 * Matches agent-bak/app/routes/tool.py surface.
 */
@Slf4j
@RestController
@RequestMapping("/tool")
@RequiredArgsConstructor
public class ToolController {

    private final ToolService toolService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public ResponseEntity<ApiResponseDto<?>> list() {
        try {
            return ResponseEntity.ok(ApiResponseDto.success("ok", toolService.list()));
        } catch (Exception e) {
            log.error("list tools error", e);
            return ResponseEntity.ok(ApiResponseDto.error(500, e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseDto<?>> get(@PathVariable Integer id) {
        try {
            var tool = toolService.getById(id);
            if (tool == null) {
                return ResponseEntity.ok(ApiResponseDto.error(404, "Tool not found"));
            }
            return ResponseEntity.ok(ApiResponseDto.success("ok", tool));
        } catch (Exception e) {
            log.error("get tool {} error", id, e);
            return ResponseEntity.ok(ApiResponseDto.error(500, e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<ApiResponseDto<?>> create(@RequestBody Map<String, Object> body) {
        try {
            String name = (body.get("name") != null ? ((String) body.get("name")).trim() : "");
            if (name.isEmpty()) {
                throw new BizException(400, "Name is required");
            }
            ToolEntity entity = objectMapper.convertValue(body, ToolEntity.class);
            toolService.create(entity);
            return ResponseEntity.ok(ApiResponseDto.success("Tool created", entity));
        } catch (BizException e) {
            return ResponseEntity.ok(ApiResponseDto.error(e.getHttpStatus(), e.getMessage()));
        } catch (Exception e) {
            log.error("create tool error", e);
            return ResponseEntity.ok(ApiResponseDto.error(500, e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponseDto<?>> update(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        try {
            var existing = toolService.getById(id);
            if (existing == null) {
                return ResponseEntity.ok(ApiResponseDto.error(404, "Tool not found"));
            }
            ToolEntity entity = objectMapper.convertValue(body, ToolEntity.class);
            var updated = toolService.update(id, entity);
            return ResponseEntity.ok(ApiResponseDto.success("Tool updated", updated));
        } catch (Exception e) {
            log.error("update tool {} error", id, e);
            return ResponseEntity.ok(ApiResponseDto.error(500, e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponseDto<?>> delete(@PathVariable Integer id) {
        try {
            boolean deleted = toolService.delete(id);
            if (!deleted) {
                return ResponseEntity.ok(ApiResponseDto.error(404, "Tool not found"));
            }
            return ResponseEntity.ok(ApiResponseDto.success("Tool deleted", null));
        } catch (Exception e) {
            log.error("delete tool {} error", id, e);
            return ResponseEntity.ok(ApiResponseDto.error(500, e.getMessage()));
        }
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponseDto<?>> patch(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        return update(id, body);
    }
}